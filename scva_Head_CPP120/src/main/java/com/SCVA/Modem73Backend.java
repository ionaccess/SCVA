package com.SCVA;

import android.os.SystemClock;

import com.SCVA.Interfaces.ModemBackend;
import com.SCVA.Utils.TruSDXCat;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import com.SCVA.Utils.Utils;
import com.SCVA.models.Configuration;

/** Processor-owned MODEM73 backend using one selected pinned codec family per configuration. */
final class Modem73Backend implements ModemBackend {
    static final String MODE="MODEM73", PROFILE="OFDM_QPSK_1_2_NORMAL", ROBUST_PROFILE="OFDM_QPSK_1_4_LONG", REPEATED_PROFILE="OFDM_QPSK_1_4X2_LONG";
    static final String QPSK_14_NORMAL=Configuration.MODEM73_PROFILE_QPSK_14_NORMAL, BPSK_14_NORMAL=Configuration.MODEM73_PROFILE_BPSK_14_NORMAL;
    static final String RDM_600=Configuration.MODEM73_PROFILE_RDM_600, RDMN_300=Configuration.MODEM73_PROFILE_RDMN_300;
    private static final String[] SUPPORTED_PROFILES={PROFILE,ROBUST_PROFILE,REPEATED_PROFILE,QPSK_14_NORMAL,BPSK_14_NORMAL,RDM_600,RDMN_300};
    static boolean isSupportedProfile(String p){if(p==null)return false;for(String value:SUPPORTED_PROFILES)if(value.equals(p))return true;return false;}
    static int nativeProfile(String p){
        if(PROFILE.equals(p))return 0x11;if(ROBUST_PROFILE.equals(p))return 0x99;if(REPEATED_PROFILE.equals(p))return 0x9d;
        if(QPSK_14_NORMAL.equals(p))return 0x19;if(BPSK_14_NORMAL.equals(p))return 0x09;
        if(RDM_600.equals(p))return 0x10001;if(RDMN_300.equals(p))return 0x10003;
        throw new IllegalArgumentException("Unsupported MODEM73 profile");
    }
    static int maxRecordBytes(String p){return BPSK_14_NORMAL.equals(p)?1815:Modem73Packet.MAX_RECORD_BYTES;}
    static final int RATE=48000, MAX_TNC_BYTES=Modem73Packet.MAX_TNC_BYTES;
    static final int VOX_TONE_HZ=1200, VOX_DEFAULT_LEAD_MS=550, VOX_MIN_LEAD_MS=100, VOX_MAX_LEAD_MS=3000;
    private static final int BLOCK=1024, CHANNEL_WINDOW=4800, DECODER_QUEUE_MAX_SAMPLES=96000;
    private static final long STOP_WAIT_MS=5000;
    private static final AtomicLong IDS=new AtomicLong();
    private static final AtomicLong INSTANCE_IDS=new AtomicLong();

    interface Events {
        void onState(Modem73Backend source,String state);
        void onProgress(Modem73Backend source,long operationId,int percent);
        void onTxStage(Modem73Backend source,long operationId,String stage);
        void onTx(Modem73Backend source,long operationId,String outcome,byte[] payload,String detail);
        void onRx(Modem73Backend source,byte[] payload);
        default void onSpectrum(Modem73Backend source,float[] levelsDbfs,int sampleRate,int fftSize,long generation){}
        void onFailure(Modem73Backend source,String detail);
    }
    interface Audio {
        boolean open(); void requestStop(); void close();
        int read(float[] dst,int offset,int count,int timeoutMs); boolean captureAlive();
        void flushCapture(); void suppressCapture(boolean suppress);
        void beginPlayback(); int write(float[] src,int offset,int count);
        int drain(int timeoutMs); void requestCancelPlayback(); int abortPlayback(); boolean failed();
        boolean beginAccess(int airMs,long seed,boolean responder,int ditherMs);
        int accessStep(float levelDb,boolean txAllowed);
        default long[] stats(){return null;}
        default String backendName(){return "unavailable";}
    }
    interface Codec {
        long create(boolean rx,String callsign); void close(long h);
        default long create(boolean rx,String callsign,String profile){return create(rx,callsign);}
        float[] encode(long h,byte[] payload); byte[][] feed(long h,float[] samples,int count);
        default long[] stats(long h){return null;}
        default byte[][] packetize(byte[] record){return packetize(record,PROFILE);}
        default byte[][] packetize(byte[] record,String profile){if(record.length>510)throw new IllegalArgumentException("Fragment codec unavailable");return new byte[][]{record.clone()};}
        default void expire(long h){}
        default long[] assemblyStats(long h){return null;}
        default boolean resetReceiver(long h){return true;}
        default long[] encodeTimings(long h){return null;}
    }
    interface Ptt { boolean enabled(); long leadMs(); long tailMs(); boolean on(); boolean off(); }
    interface Factory { Audio audio(); Codec codec(); Ptt ptt(); }

    private final Factory production=new Factory() {
        public Audio audio(){return new NativeAudio();}
        public Codec codec(){return new NativeCodec();}
        public Ptt ptt(){return new CatPtt(owner);}
    };
    private final Processor owner; private final Events events; private final Factory factory;
    private final long instanceId=INSTANCE_IDS.incrementAndGet();
    private final Object lock=new Object();
    private volatile State state=State.IDLE;
    private volatile boolean stopping,cancelRequested,pauseRx,decoderSuppressed,audioFailure;
    private BackendSelection selection; private Audio audio; private Codec codec; private Ptt ptt;
    private volatile long txDeadlineMs;
    private long lastAssemblyLogMs;private String lastAssemblySummary="";
    private long rxCodec,txCodec; private Thread captureWorker,decoderWorker,txWorker;
    private boolean catKeyed; private long currentOperationId;
    private final Object decoderQueueLock=new Object();
    private final ArrayDeque<DecoderChunk> decoderQueue=new ArrayDeque<>();
    private final Modem73SpectrumVisualizer spectrum;
    private int decoderQueuedSamples; private long decoderEpoch,decoderDiscontinuities;
    private final float[] channelWindow=new float[CHANNEL_WINDOW]; private int channelWindowAt,channelWindowCount; private double channelWindowSquares;
    private volatile float channelLevelDb=-120.0f; private volatile long channelMeasurementAtMs; private volatile long captureSamplesRead;
    private volatile long decoderSamplesFed,decoderFeedCalls,decodedPackets;
    private volatile long lastSummarySamples,lastSummaryPackets;
    private final AtomicLong parserAccepted=new AtomicLong(),parserRejected=new AtomicLong(),deliveryAccepted=new AtomicLong(),deliveryRejected=new AtomicLong(),storageReturned=new AtomicLong(),refreshRequested=new AtomicLong();

    private static final class DecoderChunk { final long epoch; final float[] samples; final boolean reset; DecoderChunk(long e,float[] s,boolean r){epoch=e;samples=s;reset=r;} }

    Modem73Backend(Processor owner,Events events){this(owner,events,null);}
    Modem73Backend(Processor owner,Events events,Factory factory){this.owner=owner;this.events=events;this.factory=factory==null?production:factory;this.spectrum=new Modem73SpectrumVisualizer((levels,rate,size,generation)->events.onSpectrum(this,levels,rate,size,generation));}
    void setSpectrumEnabled(boolean enabled){spectrum.setEnabled(enabled);}
    long spectrumGeneration(){return spectrum.generation();}
    @Override public State state(){synchronized(lock){return state;}}
    @Override public boolean busy(){synchronized(lock){return txWorker!=null&&txWorker.isAlive();}}
    long instanceId(){return instanceId;}
    long operationId(){synchronized(lock){return currentOperationId;}}
    void recordParser(boolean accepted){(accepted?parserAccepted:parserRejected).incrementAndGet();}
    void recordDelivery(boolean accepted){(accepted?deliveryAccepted:deliveryRejected).incrementAndGet();}
    void recordStorageReturned(){storageReturned.incrementAndGet();}
    void recordRefreshRequested(){refreshRequested.incrementAndGet();}
    boolean isReceivingHealthy(){
        synchronized(lock){
            if(stopping||audioFailure||state!=State.RECEIVING||captureWorker==null||!captureWorker.isAlive()||decoderWorker==null||!decoderWorker.isAlive()||audio==null||audio.failed())return false;
            long[] s=audio.stats();
            return s==null ? audio.captureAlive() : s.length>=19&&s[1]>0&&s[6]>=0&&s[6]<=2000&&s[4]==0;
        }
    }
    boolean isStartingWithWorker(){synchronized(lock){return !stopping&&state==State.STARTING&&captureWorker!=null&&captureWorker.isAlive()&&decoderWorker!=null&&decoderWorker.isAlive();}}
    private void log(String stage,String detail){owner.modem73Diagnostic(this,stage,detail);}

    @Override public void apply(BackendSelection next,boolean image){
        log("codec_create_start","direction=rx_tx");
        synchronized(lock){
            if(stopping)throw new IllegalStateException("MODEM73 is stopping");
            if(next==null||!MODE.equals(next.modeName())||next.configuration().getId()==9)
                throw new IllegalArgumentException("MODEM73 requires a supported configuration");
            if(next.configuration().isEncryption()&&!Modem73Crypto.supports(next.configuration().getEncryptionType()))throw new IllegalArgumentException("Unsupported MODEM73 encryption algorithm");
            if(!isSupportedProfile(next.modem73Profile())||!Modem73Packet.validCallsign(next.modem73Callsign()))
                throw new IllegalArgumentException("Enter a valid MODEM73 callsign and supported profile");
            if(selection!=null&&selection.sameAs(next))return;
            if(selection!=null||captureWorker!=null||decoderWorker!=null||txWorker!=null)throw new IllegalStateException("MODEM73 configuration requires a quiescent backend");
            codec=factory.codec();ptt=factory.ptt();
            rxCodec=codec.create(true,null,next.modem73Profile());txCodec=codec.create(false,next.modem73Callsign(),next.modem73Profile());
            if(rxCodec==0||txCodec==0){if(rxCodec!=0)codec.close(rxCodec);if(txCodec!=0)codec.close(txCodec);rxCodec=txCodec=0;throw new IllegalStateException("MODEM73 codec initialization failed");}
            selection=next;state=State.SELECTED;
        }
        log("codec_create_complete","rx=ready tx=ready profile="+next.modem73Profile()+" decoderMode=frame_auto_detect voxEnabled="+next.modem73VoxEnabled()+" voxLeadMs="+next.modem73VoxLeadMs());
    }

    @Override public void receive(){
        log("receive_requested","mode=MODEM73 requestedRate="+RATE+" channels=1 format=float32");
        Audio openedAudio=null;
        synchronized(lock){
            if(stopping||selection==null)throw new IllegalStateException("MODEM73 selection is not ready");
            if(audioFailure||state==State.FAILED)throw new IllegalStateException("MODEM73 audio/receive owner failed and must be quiesced before recreation");
            boolean captureAlive=captureWorker!=null&&captureWorker.isAlive(),decoderAlive=decoderWorker!=null&&decoderWorker.isAlive();
            if(captureAlive&&decoderAlive)return;
            if(captureAlive||decoderAlive)throw new IllegalStateException("MODEM73 receive workers are partially alive; cleanup is required before restart");
            if(audio==null){audio=factory.audio();if(!audio.open()){audio.close();audio=null;state=State.FAILED;throw new IllegalStateException("miniaudio 48 kHz mono float input/output unavailable");}openedAudio=audio;}
            state=State.STARTING;pauseRx=false;decoderSuppressed=false;
            spectrum.start();
            decoderWorker=new Thread(this::decoderLoop,"SCVA-MODEM73-DECODER");
            captureWorker=new Thread(this::captureLoop,"SCVA-MODEM73-CAPTURE");
            decoderWorker.start();captureWorker.start();
        }
        if(openedAudio!=null){log("audio_open_complete","requestedRate=48000 channels=1 format=float32");logAudioConfiguration(openedAudio);}
        log("decoder_worker_start","state=STARTING");log("capture_worker_start","state=STARTING consumer=native_capture_ring_only");
    }

    private void captureLoop(){
        float[] samples=new float[BLOCK];long started=SystemClock.elapsedRealtime();boolean expectedExit=false;String exitReason="unexpected_worker_exit";
        try{
            log("capture_worker_enter","blockSamples="+BLOCK+" rate="+RATE+" role=sole_native_capture_ring_consumer");
            while(!stopping&&!audioFailure){
                Audio a=audio;if(a==null)throw new IllegalStateException("capture audio owner disappeared");int n=a.read(samples,0,samples.length,100);
                if(n>0&&!stopping){updateChannelMeasurement(samples,n);captureSamplesRead+=n;if(!pauseRx&&!decoderSuppressed){enqueueDecoderSamples(samples,n);spectrum.offer(samples,n);}if(state==State.STARTING)setState(State.RECEIVING,"capture_samples_consumed");}
                else if(n==-2&&!stopping)throw new IllegalStateException("capture ring/device failure");
                else if(n==-1&&stopping){expectedExit=true;exitReason="stop_requested";break;}
                else if(n==0&&!stopping){
                    if(a.failed())throw new IllegalStateException("miniaudio capture reported failure");
                    long[] stats=a.stats();long age=stats!=null&&stats.length>=7?stats[6]:-1;
                    if(state==State.STARTING&&SystemClock.elapsedRealtime()-started>5000)throw new IllegalStateException("capture produced no samples within 5000 ms");
                    if(state==State.RECEIVING&&age>2000)throw new IllegalStateException("capture callbacks stopped for "+age+" ms");
                }
                emitRxSummary(a,false);
            }
            if(stopping){expectedExit=true;exitReason="stop_requested";}
        }catch(RuntimeException failure){if(!stopping){exitReason=safeMessage(failure);failRuntime("capture_failed",exitReason);}}
        finally{
            State next;synchronized(lock){next=stopping?State.STOP_PENDING:audioFailure?State.FAILED:pauseRx?State.PAUSED:expectedExit?State.FAILED:State.FAILED;state=next;lock.notifyAll();}
            synchronized(decoderQueueLock){decoderQueueLock.notifyAll();}
            Audio currentAudio=audio;if(currentAudio!=null)emitRxSummary(currentAudio,true);
            log("capture_worker_exit","state="+next+" reason="+exitReason+" samplesRead="+captureSamplesRead+" decoderSamplesFed="+decoderSamplesFed+" feeds="+decoderFeedCalls+" packets="+decodedPackets);
        }
    }

    private void decoderLoop(){
        long appliedEpoch=0;
        try{
            log("decoder_worker_enter","queueBoundSamples="+DECODER_QUEUE_MAX_SAMPLES);
            while(!stopping&&!audioFailure){
                DecoderChunk item;
                synchronized(decoderQueueLock){while(decoderQueue.isEmpty()&&!stopping&&!audioFailure)decoderQueueLock.wait(100);if(stopping||audioFailure)break;item=decoderQueue.pollFirst();if(item!=null&&item.samples!=null)decoderQueuedSamples-=item.samples.length;}
                if(item==null){codec.expire(rxCodec);emitAssemblySummary();continue;}
                if(item.reset||item.epoch!=appliedEpoch){if(!codec.resetReceiver(rxCodec))throw new IllegalStateException("decoder reset failed after capture discontinuity");appliedEpoch=item.epoch;log("decoder_reset","epoch="+appliedEpoch+" reason="+(item.reset?"explicit_discontinuity":"epoch_change"));continue;}
                byte[][] packets=codec.feed(rxCodec,item.samples,item.samples.length);decoderSamplesFed+=item.samples.length;decoderFeedCalls++;emitAssemblySummary();
                if(packets!=null)for(byte[] packet:packets)if(packet!=null&&packet.length>0&&!stopping&&!audioFailure){decodedPackets++;log("codec_packet","bytes="+packet.length);events.onRx(this,packet.clone());}
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();if(!stopping)failRuntime("decoder_failed","decoder_queue_wait_interrupted");}
         catch(RuntimeException failure){if(!stopping)failRuntime("decoder_failed",safeMessage(failure));}
        finally{log("decoder_worker_exit","state="+(stopping?"STOP_PENDING":audioFailure?"FAILED":"STOPPED")+" samplesFed="+decoderSamplesFed+" feeds="+decoderFeedCalls+" packets="+decodedPackets);}
    }

    private void emitAssemblySummary(){
        long now=SystemClock.elapsedRealtime();if(now-lastAssemblyLogMs<1000)return;lastAssemblyLogMs=now;
        long[] v=codec.assemblyStats(rxCodec);if(v==null||v.length<9)return;
        String value="pending="+v[0]+" bytes="+v[1]+" completed="+v[2]+" rejected="+v[3]+" expired="+v[4]+" packetId="+v[5]+" fragmentIndex="+(v[6]+1)+" status="+v[7]+" lastRejectReason="+v[8];
        if(!value.equals(lastAssemblySummary)){lastAssemblySummary=value;log("rx_reassembly",value);}
    }

    private void failRuntime(String stage,String reason){
        synchronized(lock){if(stopping)return;audioFailure=true;cancelRequested=true;state=State.FAILED;lock.notifyAll();}
        synchronized(decoderQueueLock){decoderQueueLock.notifyAll();}
        Audio a=audio;if(a!=null)try{a.requestStop();}catch(RuntimeException ignored){}
        spectrum.stop();log(stage,"reason="+reason+" admission=closed recovery=quiesce_then_recreate");events.onFailure(this,"MODEM73 receive failed: "+reason);
    }

    private void setState(State next,String reason){synchronized(lock){if(stopping||audioFailure)return;state=next;lock.notifyAll();}events.onState(this,next+":"+reason);log("state_transition","state="+next+" reason="+reason);}

    private void updateChannelMeasurement(float[] samples,int count){
        for(int i=0;i<count;i++){float sample=Float.isFinite(samples[i])?samples[i]:0.0f;float old=channelWindowCount<CHANNEL_WINDOW?0.0f:channelWindow[channelWindowAt];if(channelWindowCount<CHANNEL_WINDOW)channelWindowCount++;else channelWindowSquares-=(double)old*old;channelWindow[channelWindowAt]=sample;channelWindowSquares+=(double)sample*sample;if(++channelWindowAt==CHANNEL_WINDOW)channelWindowAt=0;}
        double rms=channelWindowCount==0?0:Math.sqrt(Math.max(0,channelWindowSquares)/channelWindowCount);channelLevelDb=(float)(20.0*Math.log10(Math.max(rms,1.0e-9)));channelMeasurementAtMs=SystemClock.elapsedRealtime();
    }

    private void enqueueDecoderSamples(float[] samples,int count){
        if(count<=0||decoderSuppressed||pauseRx)return;
        synchronized(decoderQueueLock){
            if(decoderSuppressed||pauseRx||stopping)return;
            if(decoderQueuedSamples+count>DECODER_QUEUE_MAX_SAMPLES){int dropped=decoderQueuedSamples;decoderQueue.clear();decoderQueuedSamples=0;long epoch=++decoderEpoch;decoderDiscontinuities++;decoderQueue.addLast(new DecoderChunk(epoch,null,true));log("decoder_discontinuity","reason=bounded_queue_overflow droppedQueuedSamples="+dropped+" currentBlockSamples="+count+" epoch="+epoch);}
            float[] copy=java.util.Arrays.copyOf(samples,count);decoderQueue.addLast(new DecoderChunk(decoderEpoch,copy,false));decoderQueuedSamples+=count;decoderQueueLock.notifyAll();
        }
    }

    private void decoderDiscontinuity(String reason){
        synchronized(decoderQueueLock){int dropped=decoderQueuedSamples;decoderQueue.clear();decoderQueuedSamples=0;long epoch=++decoderEpoch;decoderDiscontinuities++;decoderQueue.addLast(new DecoderChunk(epoch,null,true));decoderQueueLock.notifyAll();log("decoder_discontinuity","reason="+reason+" droppedQueuedSamples="+dropped+" epoch="+epoch);}
    }
    private void emitRxSummary(Audio a,boolean force){
        if(!Utils.log)return;long now=SystemClock.elapsedRealtime();if(!force&&now-lastSummaryMs<2000)return;lastSummaryMs=now;
        long[] s=a.stats();long cb=s!=null&&s.length>=19?s[0]:-1,frames=s!=null&&s.length>=19?s[1]:-1,queued=s!=null&&s.length>=19?s[5]:-1,age=s!=null&&s.length>=19?s[6]:-1;
        long rms=s!=null&&s.length>=19?s[7]:-1,peak=s!=null&&s.length>=19?s[8]:-1,dropped=s!=null&&s.length>=19?s[4]:-1;
        long queuedTotal=s!=null&&s.length>=19?s[2]:-1,suppressed=s!=null&&s.length>=19?s[3]:-1,flushed=s!=null&&s.length>=19?s[18]:-1,nullCallbacks=s!=null&&s.length>=19?s[17]:-1;
        long[] cs=codec.stats(rxCodec);long codecFrames=cs!=null&&cs.length>=9?cs[0]:-1,validFrames=cs!=null&&cs.length>=9?cs[1]:-1,rejectedFrames=cs!=null&&cs.length>=9?cs[2]:-1;
        long syncs=cs!=null&&cs.length>=9?cs[3]:-1,preambleRejects=cs!=null&&cs.length>=9?cs[4]:-1,symbolErrors=cs!=null&&cs.length>=9?cs[5]:-1,crcErrors=cs!=null&&cs.length>=9?cs[6]:-1,erasedSymbols=cs!=null&&cs.length>=9?cs[7]:-1,lastSnrMilliDb=cs!=null&&cs.length>=9?cs[8]:0;
        long sampleDelta=decoderSamplesFed-lastSummarySamples,packetDelta=decodedPackets-lastSummaryPackets;lastSummarySamples=decoderSamplesFed;lastSummaryPackets=decodedPackets;
        String condition=cb==0?"NO_CAPTURE_CALLBACKS":frames==0?"NO_CAPTURE_FRAMES":channelLevelDb<=-60?"CAPTURED_SILENCE":decoderSuppressed?"DECODER_SUPPRESSED_LOCAL_TX":pauseRx?"DECODER_PAUSED":sampleDelta==0?"CAPTURED_SIGNAL_NO_DECODER_INPUT":packetDelta==0?"CAPTURED_SIGNAL_NO_DECODED_FRAME":"DECODED_FRAME_ACTIVITY";
        int javaQueued;long epoch; synchronized(decoderQueueLock){javaQueued=decoderQueuedSamples;epoch=decoderEpoch;}
        log("rx_summary","condition="+condition+" profile="+(selection==null?"none":selection.modem73Profile())+" voxEnabled="+(selection!=null&&selection.modem73VoxEnabled())+" voxLeadMs="+(selection==null?0:selection.modem73VoxLeadMs())+" captureCallbacks="+cb+" captureFrames="+frames+" nullInputCallbacks="+nullCallbacks+" callbackAgeMs="+age+" captureQueueSamples="+queued+" captureSamplesRead="+captureSamplesRead+" channelLevelDb="+channelLevelDb+" channelMeasurementAgeMs="+(channelMeasurementAtMs==0?-1:now-channelMeasurementAtMs)+" decoderSamples="+decoderSamplesFed+" decoderSampleDelta="+sampleDelta+" decoderFeeds="+decoderFeedCalls+" decodedPackets="+decodedPackets+" decodedPacketDelta="+packetDelta+" nativeFrameCallbacks="+codecFrames+" nativeValidFrames="+validFrames+" nativeRejectedLengths="+rejectedFrames+" syncDetections="+syncs+" preambleRejects="+preambleRejects+" fecSymbolErrors="+symbolErrors+" crcFailures="+crcErrors+" erasedSymbols="+erasedSymbols+" lastSnrMilliDb="+lastSnrMilliDb+" parserAccepted="+parserAccepted.get()+" parserRejected="+parserRejected.get()+" deliveryAccepted="+deliveryAccepted.get()+" deliveryRejected="+deliveryRejected.get()+" storageReturned="+storageReturned.get()+" refreshRequested="+refreshRequested.get()+" decoderQueueSamples="+javaQueued+" decoderEpoch="+epoch+" decoderDiscontinuities="+decoderDiscontinuities+" captureSuppressedFrames="+suppressed+" captureDroppedFrames="+dropped+" captureFlushedFrames="+flushed+" captureRmsMicro="+rms+" capturePeakMicro="+peak+" peakNearFullScalePossibleClip="+(peak>=990000)+" captureWorker="+(captureWorker!=null&&captureWorker.isAlive()?"alive":"dead")+" decoderWorker="+(decoderWorker!=null&&decoderWorker.isAlive()?"alive":"dead"));
        log("spectrum_summary","enabled="+spectrum.enabled()+" offeredChunks="+spectrum.offeredChunks()+" droppedChunks="+spectrum.droppedChunks()+" emittedFrames="+spectrum.emittedFrames()+" queuedChunks="+spectrum.queuedChunks());
    }
    private void logAudioConfiguration(Audio a){long[] s=a.stats();if(s==null||s.length<19){log("audio_open_complete","stats=unavailable backend="+a.backendName()+" captureAlive="+a.captureAlive());return;}log("audio_open_complete","backend="+a.backendName()+" captureRate="+s[9]+" captureChannels="+s[10]+" captureFormat="+s[11]+" captureBackendId="+s[12]+" playbackRate="+s[13]+" playbackChannels="+s[14]+" playbackFormat="+s[15]+" playbackBackendId="+s[16]);}
    private volatile long lastSummaryMs;

    @Override public void text(String text){
        BackendSelection selected;synchronized(lock){selected=selection;if(selected==null||selected.configuration().isEncryption())throw new IllegalStateException("MODEM73 encrypted selection requires an explicitly prepared encrypted record");}
        record(Modem73Packet.prepare(selected.modem73Callsign(),text).payload);
    }
    void record(byte[] record){
        final Modem73Packet.Admission admission=Modem73Packet.record(record);final Modem73Packet.Received typed=Modem73Packet.receive(record);
        synchronized(lock){
            if(stopping||state!=State.RECEIVING||busy()||selection==null)throw new IllegalStateException("MODEM73 is not ready");
            if(typed.encrypted&&!typed.secret.callsign.equals(selection.modem73Callsign()))throw new IllegalArgumentException("MODEM73 sender configuration changed");
            if(typed.encrypted!=selection.configuration().isEncryption()||typed.encrypted&&!typed.algorithm.equals(selection.configuration().getEncryptionType()))throw new IllegalArgumentException("MODEM73 prepared record does not match selected encryption settings");
            if(admission.payload.length>maxRecordBytes(selection.modem73Profile()))throw new IllegalArgumentException("Selected MODEM73 profile supports at most "+maxRecordBytes(selection.modem73Profile())+" application-record bytes because of the bounded reassembly deadline");
            final byte[][] frames=codec.packetize(admission.payload,selection.modem73Profile());
            if(frames==null||frames.length<1||frames.length>32)throw new IllegalArgumentException("MODEM73 record exceeds the selected profile's bounded fragment capacity");
            for(byte[] frame:frames)if(frame==null||frame.length<1||frame.length>MAX_TNC_BYTES)throw new IllegalArgumentException("MODEM73 selected profile produced an invalid fragment");
            final long op=IDS.incrementAndGet();cancelRequested=false;currentOperationId=op;txWorker=new Thread(()->transmit(op,admission,frames),"SCVA-MODEM73-TX");events.onTxStage(this,op,"QUEUED");txWorker.start();
        }
    }
    long lastOperationId(){synchronized(lock){return currentOperationId;}}
    static long nextOperationId(){return IDS.incrementAndGet();}

    private void transmit(long op,Modem73Packet.Admission admission,byte[][] frames){
        String outcome="FAILED",detail="transmission failed";boolean keyed=false,pttAttempted=false;float[] pcm=null;Audio a=audio;Ptt radio=ptt;
        try{
            log("tx_worker_start","direction=tx preparedBytes="+admission.payload.length);
            BackendSelection selected=selection;
            final boolean vox=selected.modem73VoxEnabled();
            final int leadMs=vox?selected.modem73VoxLeadMs():0;
            txDeadlineMs=SystemClock.elapsedRealtime()+900000L+frames.length*(long)leadMs;
            int packetId=frames.length>1?((frames[0][1]&255)<<8)|(frames[0][2]&255):-1;
            log("tx_fragments","operation="+op+" packetId="+packetId+" count="+frames.length+" recordBytes="+admission.payload.length);
            long lastFrameCompletedMs=0;
            for(int fragment=0;fragment<frames.length;fragment++){
                if(frames[fragment]==null||frames[fragment].length<1||frames[fragment].length>510)throw new IllegalStateException("Invalid MODEM73 frame length");
                pcm=null;
                try{
            checkCancelled();long encodeStart=SystemClock.elapsedRealtime();log("tx_codec_lock_wait_start","direction=tx captureAndDecoderRemainLive=true");
            pcm=codec.encode(txCodec,frames[fragment]);long encodeElapsed=SystemClock.elapsedRealtime()-encodeStart;
            if(pcm==null||pcm.length==0)throw new IllegalStateException("encoder returned no PCM");
            float[] voxLead=vox?buildVoxLead(RATE,VOX_TONE_HZ,leadMs):null;
            long[] timings=codec.encodeTimings(txCodec);if(timings!=null&&timings.length>=4)log("tx_encode_timing","lockWaitMs="+(timings[0]/1000000.0)+" encoderMs="+(timings[1]/1000000.0)+" inputCopyMs="+(timings[2]/1000000.0)+" pcmReturnCopyMs="+(timings[3]/1000000.0)+" javaCallElapsedMs="+encodeElapsed);
            log("tx_encode_complete","direction=tx fragment="+(fragment+1)+"/"+frames.length+" pcmSamples="+pcm.length+" durationMs="+(pcm.length*1000L/RATE));checkCancelled();
            if(!waitForChannel(pcm.length+(voxLead==null?0:voxLead.length)))throw new IllegalStateException("upstream channel access rejected transmission");
            log("channel_access_granted","direction=tx");
            if(lastFrameCompletedMs>0&&SystemClock.elapsedRealtime()-lastFrameCompletedMs>150000)throw new IllegalStateException("MODEM73 fragment idle budget exceeded 150 seconds");
            checkCancelled();decoderSuppressed=true;decoderDiscontinuity("half_duplex_local_tx_start");
            boolean useCatPtt=!vox&&radio.enabled();
            if(useCatPtt){pttAttempted=true;boolean asserted=radio.on();log("ptt_assert_result","commandAccepted="+asserted+" physicalConfirmation=unavailable");if(!asserted)throw new IllegalStateException("CAT PTT assertion command failed");keyed=true;catKeyed=true;sleepInterruptibly(Math.max(500,radio.leadMs()));}
            checkCancelled();if(fragment==0)events.onTxStage(this,op,"TRANSMITTING");log("tx_playback_start","pcmSamples="+pcm.length+" voxLeadSamples="+(voxLead==null?0:voxLead.length));a.beginPlayback();
            if(voxLead!=null)writePlayback(a,voxLead);
            int offset=0,zeroProgress=0;
            while(offset<pcm.length){checkCancelled();int remaining=Math.min(BLOCK,pcm.length-offset);int n=a.write(pcm,offset,remaining);if(n<0||n>remaining)throw new IllegalStateException("miniaudio playback write failed");if(n==0){if(++zeroProgress>200)throw new IllegalStateException("miniaudio playback queue made no progress");sleepInterruptibly(5);continue;}zeroProgress=0;offset+=n;owner.modem73Progress(this,op,(int)Math.min(95,((long)fragment*pcm.length+offset)*95/(frames.length*pcm.length)));}
            log("tx_audio_queued","direction=tx samplesWritten="+offset+" totalSamples="+pcm.length);
            int drain=a.drain((int)Math.min(Integer.MAX_VALUE,(pcm.length+(voxLead==null?0:voxLead.length))*1000L/RATE+12000));log("tx_playback_drain","direction=tx result="+drain);checkCancelled();if(drain!=1)throw new IllegalStateException(drain==0?"miniaudio playback drain timed out":"miniaudio playback drain failed");
            if(a.failed())throw new IllegalStateException("miniaudio reported audio failure during playback");
            if(useCatPtt&&radio.tailMs()>0)sleepInterruptibly(radio.tailMs());
                }finally{
                    boolean released=true;
                    if(pttAttempted||keyed||catKeyed){try{released=radio.off();}catch(RuntimeException failure){released=false;}catKeyed=false;keyed=false;pttAttempted=false;log("ptt_release_result","commandAccepted="+released+" fragment="+(fragment+1)+" physicalConfirmation=unavailable");}
                    if(decoderSuppressed){decoderSuppressed=false;decoderDiscontinuity("half_duplex_local_tx_end");}
                    if(!released)throw new IllegalStateException("CAT PTT release command failed; physical state is unconfirmed");
                }
                checkCancelled();lastFrameCompletedMs=SystemClock.elapsedRealtime();owner.modem73Progress(this,op,(fragment+1)*95/frames.length);
            }
            outcome="AUDIO_COMPLETED";detail="miniaudio callback consumed samples and estimated device-buffer tail elapsed; physical acoustic completion is not confirmed";
        }catch(Cancelled cancelled){outcome="CANCELLED";detail="cancelled";}
         catch(InterruptedException interrupted){Thread.currentThread().interrupt();outcome="CANCELLED";detail="interrupted";}
         catch(RuntimeException failure){detail=failure.getClass().getSimpleName()+": "+safeMessage(failure);if(a!=null&&a.failed()&&!stopping&&!audioFailure)failRuntime("audio_failed","audio_failure_during_tx");}
        finally{
            if(a!=null){try{if(!"AUDIO_COMPLETED".equals(outcome))a.abortPlayback();}catch(RuntimeException ignored){}}
            if(pttAttempted||keyed||catKeyed){boolean released=false;try{released=radio.off();}catch(RuntimeException ignored){}catKeyed=false;log("ptt_release_result","commandAccepted="+released+" physicalConfirmation=unavailable");if(!released){outcome="FAILED";detail="CAT PTT release command failed; physical state is unconfirmed";}}
            if(decoderSuppressed){decoderSuppressed=false;decoderDiscontinuity("half_duplex_local_tx_end");}
            synchronized(lock){cancelRequested=false;if(stopping)state=State.STOP_PENDING;else if(audioFailure||a==null||a.failed())state=State.FAILED;else if(captureWorker!=null&&captureWorker.isAlive()&&decoderWorker!=null&&decoderWorker.isAlive()){state=State.RECEIVING;}else state=State.FAILED;lock.notifyAll();}
            txDeadlineMs=0;log("tx_terminal","direction=tx outcome="+outcome+" detail="+detail);
            events.onTx(this,op,outcome,admission.payload.clone(),detail);
        }
    }

    /** Upstream kiss_tnc.hh defaults: 1200 Hz, 550 ms. This tone is visualization/TX-only audio, never decoder input. */
    static float[] buildVoxLead(int sampleRate,int toneHz,int durationMs) {
        if(sampleRate<=0||toneHz<=0||toneHz>=sampleRate/2||durationMs<VOX_MIN_LEAD_MS||durationMs>VOX_MAX_LEAD_MS)throw new IllegalArgumentException("Invalid MODEM73 VOX tone settings");
        int count=(int)(((long)sampleRate*durationMs+500L)/1000L);float[] tone=new float[count];
        for(int i=0;i<count;i++)tone[i]=(float)(0.5*Math.sin(2.0*Math.PI*toneHz*i/sampleRate));
        return tone;
    }

    private void writePlayback(Audio a,float[] samples)throws InterruptedException {
        int offset=0,zeroProgress=0;
        while(offset<samples.length){checkCancelled();int requested=Math.min(BLOCK,samples.length-offset);int n=a.write(samples,offset,requested);if(n<0||n>requested)throw new IllegalStateException("miniaudio playback write failed");if(n==0){if(++zeroProgress>200)throw new IllegalStateException("miniaudio playback queue made no progress");sleepInterruptibly(5);continue;}zeroProgress=0;offset+=n;}
    }

    private boolean waitForChannel(int sampleCount) throws InterruptedException {
        Audio a=audio;long airMs=Math.max(1,(sampleCount*1000L)/RATE);long seed=System.nanoTime();
        if(!a.beginAccess((int)Math.min(Integer.MAX_VALUE,airMs),seed,false,0))throw new IllegalStateException("upstream CSMA initialization failed");
        log("channel_access_start","airtimeMs="+airMs+" source=capture_pump_rolling_rms");long startSamples=captureSamplesRead,lastAdvancedAt=SystemClock.elapsedRealtime();long deadline=SystemClock.elapsedRealtime()+Math.max(30000,8*airMs);long lastMeasurementSamples=startSamples;
        while(!stopping&&!cancelRequested&&SystemClock.elapsedRealtime()<deadline){
            checkCancelled();if(audioFailure||a.failed())throw new IllegalStateException("capture unavailable during upstream CSMA");
            Thread.sleep(25);long now=SystemClock.elapsedRealtime();long samples=captureSamplesRead;
            if(samples<=lastMeasurementSamples){if(now-lastAdvancedAt>500)throw new IllegalStateException("capture unavailable during upstream CSMA: no fresh samples");continue;}
            lastMeasurementSamples=samples;lastAdvancedAt=now;long measurementAge=channelMeasurementAtMs==0?Long.MAX_VALUE:now-channelMeasurementAtMs;long[] stats=a.stats();long ringQueued=stats!=null&&stats.length>=6?stats[5]:0;
            if(measurementAge>250||ringQueued>4800)throw new IllegalStateException("capture unavailable during upstream CSMA: measurement stale or capture ring backlog");
            int verdict=a.accessStep(channelLevelDb,true);if(verdict==1){log("channel_access_grant","freshSamples="+(samples-startSamples)+" levelDb="+channelLevelDb+" measurementAgeMs="+measurementAge);return true;}if(verdict==2)throw new IllegalStateException("upstream CSMA busy limit expired; transmission rejected");if(verdict==3)throw new IllegalStateException("upstream CSMA capture/deaf limit expired; transmission rejected");
        }
        if(stopping||cancelRequested)throw new Cancelled();throw new IllegalStateException("upstream CSMA timed out; transmission rejected");
    }
    private void checkCancelled(){if(txDeadlineMs>0&&SystemClock.elapsedRealtime()>txDeadlineMs)throw new IllegalStateException("MODEM73 logical transmission exceeded 900 seconds");if(audioFailure)throw new IllegalStateException("MODEM73 capture/audio failure requires backend recovery");if(stopping||cancelRequested)throw new Cancelled();}
    private void sleepInterruptibly(long ms)throws InterruptedException{long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){checkCancelled();Thread.sleep(Math.min(25,end-SystemClock.elapsedRealtime()));}}
    private static String safeMessage(RuntimeException e){String m=e.getMessage();return m==null?"operation failed":m.replace('\n',' ').replace('\r',' ');}

    @Override public void pause(){pauseRx=true;decoderDiscontinuity("explicit_pause");synchronized(lock){if(stopping)return;state=State.PAUSED;}events.onState(this,"PAUSED:explicit_pause");log("decoder_pause_requested","capturePump=continues");}
    @Override public void resume(){synchronized(lock){if(stopping)throw new IllegalStateException("stopping");if(audioFailure||state==State.FAILED)throw new IllegalStateException("MODEM73 capture failure requires quiescent recreation");}pauseRx=false;decoderDiscontinuity("explicit_resume");synchronized(lock){state=State.STARTING;}events.onState(this,"STARTING:explicit_resume");log("decoder_resume_requested","capturePump=already_running");}
    @Override public State stop(){Audio a;synchronized(lock){if(stopping)return State.STOP_PENDING;stopping=true;cancelRequested=true;pauseRx=false;decoderSuppressed=false;state=State.STOP_PENDING;a=audio;}spectrum.stop();synchronized(decoderQueueLock){decoderQueueLock.notifyAll();}log("stop_requested","admission=closed captureWorker="+alive(captureWorker)+" decoderWorker="+alive(decoderWorker)+" txWorker="+alive(txWorker));if(a!=null)try{a.requestStop();log("audio_stop_signalled","capture_and_playback");}catch(RuntimeException failure){log("audio_stop_signal_failed",failure.getClass().getSimpleName());}return State.STOP_PENDING;}
    @Override public State awaitStop(long timeoutMillis)throws InterruptedException{
        long deadline=timeoutMillis<=0?Long.MAX_VALUE:SystemClock.elapsedRealtime()+timeoutMillis;
        while(true){Thread c,d,t;synchronized(lock){c=captureWorker;d=decoderWorker;t=txWorker;}if(!alive(c)&&!alive(d)&&!alive(t))break;if(c!=null&&c!=Thread.currentThread())c.join(joinSlice(deadline));if(d!=null&&d!=Thread.currentThread())d.join(joinSlice(deadline));if(t!=null&&t!=Thread.currentThread())t.join(joinSlice(deadline));if(deadline!=Long.MAX_VALUE&&SystemClock.elapsedRealtime()>=deadline&&(alive(c)||alive(d)||alive(t))){log("stop_pending","captureAlive="+alive(c)+" decoderAlive="+alive(d)+" txAlive="+alive(t)+" resourcesRetained=true");return State.STOP_PENDING;}}
        while(!spectrum.awaitStop(joinSlice(deadline)))if(deadline!=Long.MAX_VALUE&&SystemClock.elapsedRealtime()>=deadline){log("stop_pending","spectrumWorkerAlive=true resourcesRetained=true");return State.STOP_PENDING;}
        synchronized(lock){if(audio!=null){audio.close();audio=null;}if(rxCodec!=0){codec.close(rxCodec);rxCodec=0;}if(txCodec!=0){codec.close(txCodec);txCodec=0;}captureWorker=decoderWorker=txWorker=null;synchronized(decoderQueueLock){decoderQueue.clear();decoderQueuedSamples=0;}state=State.STOPPED;lock.notifyAll();}
        log("resources_released","workersQuiesced=true audioAndCodec=closed");return State.STOPPED;
    }
    private static boolean alive(Thread t){return t!=null&&t.isAlive();}
    private static long joinSlice(long deadline){return deadline==Long.MAX_VALUE?1000:Math.max(1,Math.min(1000,deadline-SystemClock.elapsedRealtime()));}
    @Override public void cancel(){synchronized(lock){cancelRequested=true;}Audio a=audio;if(a!=null)try{a.requestCancelPlayback();}catch(RuntimeException ignored){}}
    @Override public void image(String p){throw unsupported();}@Override public void key(String p){throw unsupported();}@Override public void binary(byte[] b){throw unsupported();}@Override public void diagnostic(int n){throw unsupported();}@Override public void readableDiagnostic(int n){throw unsupported();}@Override public void tune(){throw unsupported();}@Override public void frequency(double v,String s){throw unsupported();}@Override public void rsid(boolean r,boolean e){}@Override public void listener(com.SCVA.Interfaces.CallBack c){}
    private static UnsupportedOperationException unsupported(){return new UnsupportedOperationException("MODEM73 plaintext trial does not support this operation");}
    private static final class Cancelled extends RuntimeException{private static final long serialVersionUID=1L;}

    private static final class NativeAudio implements Audio{
        private long h;public boolean open(){h=nativeAudioCreate();return h!=0;}public void requestStop(){if(h!=0)nativeAudioStop(h);}public void close(){long x=h;h=0;if(x!=0)nativeAudioClose(x);}
        public int read(float[] d,int o,int n,int t){if(o<0||n<0||o>d.length||n>d.length-o)return -2;if(o==0)return nativeAudioRead(h,d,n,t);float[] part=new float[n];int read=nativeAudioRead(h,part,n,t);if(read>0)System.arraycopy(part,0,d,o,read);return read;}public boolean captureAlive(){return nativeAudioAlive(h);}public long[] stats(){return h==0?null:nativeAudioStats(h);}public String backendName(){return h==0?"unavailable":nativeAudioBackendName(h);}public void flushCapture(){nativeAudioFlushCapture(h);}public void suppressCapture(boolean b){nativeAudioSuppressCapture(h,b);}
        public void beginPlayback(){nativeAudioBeginPlayback(h);}public int write(float[] s,int o,int n){return nativeAudioWrite(h,s,o,n);}public int drain(int t){return nativeAudioDrain(h,t);}public void requestCancelPlayback(){nativeAudioRequestCancel(h);}public int abortPlayback(){return nativeAudioAbortPlayback(h);}public boolean failed(){return nativeAudioFailed(h);}
        public boolean beginAccess(int ms,long seed,boolean responder,int dither){return nativeAudioBeginAccess(h,ms,seed,responder,dither)==1;}public int accessStep(float db,boolean allowed){return nativeAudioAccessStep(h,db,allowed);}
    }
    private static final class NativeCodec implements Codec{public long create(boolean rx,String c){return create(rx,c,PROFILE);}public long create(boolean rx,String c,String p){return nativeCreate(rx,c,nativeProfile(p));}public void close(long h){nativeClose(h);}public float[] encode(long h,byte[] b){return nativeEncode(h,b);}public byte[][] feed(long h,float[] s,int n){return nativeFeed(h,s,n);}public long[] stats(long h){return nativeCodecStats(h);}public boolean resetReceiver(long h){return nativeResetRx(h);}public long[] encodeTimings(long h){return nativeEncodeTimings(h);}public byte[][] packetize(byte[] r){return packetize(r,PROFILE);}public byte[][] packetize(byte[] r,String p){return nativePacketize(r,nativeProfile(p));}public void expire(long h){nativeExpire(h);}public long[] assemblyStats(long h){return nativeAssemblyStats(h);}}
    private static final class CatPtt implements Ptt{
        private final Processor processor;CatPtt(Processor p){processor=p;}
        public boolean enabled(){return processor.isCatEnabled();}
        public long leadMs(){return processor.catLeadMs();}
        public long tailMs(){return processor.catTailMs();}public boolean on(){return TruSDXCat.getInstance().send("TX0;");}public boolean off(){return TruSDXCat.getInstance().send("RX;");}
    }

    private static native byte[][] nativePacketize(byte[] record,int profileMode);private static native void nativeExpire(long h);private static native long[] nativeAssemblyStats(long h);
    private static native long nativeCreate(boolean receive,String callsign,int profileMode);private static native void nativeClose(long handle);private static native float[] nativeEncode(long handle,byte[] payload);private static native byte[][] nativeFeed(long handle,float[] samples,int count);private static native long[] nativeCodecStats(long handle);private static native boolean nativeResetRx(long handle);private static native long[] nativeEncodeTimings(long handle);
    private static native long nativeAudioCreate();private static native void nativeAudioStop(long handle);private static native void nativeAudioClose(long handle);private static native int nativeAudioRead(long handle,float[] destination,int count,int timeoutMs);private static native boolean nativeAudioAlive(long handle);private static native long[] nativeAudioStats(long handle);private static native String nativeAudioBackendName(long handle);private static native void nativeAudioFlushCapture(long handle);private static native void nativeAudioSuppressCapture(long handle,boolean suppress);private static native void nativeAudioBeginPlayback(long handle);private static native int nativeAudioWrite(long handle,float[] source,int offset,int count);private static native int nativeAudioDrain(long handle,int timeoutMs);private static native void nativeAudioRequestCancel(long handle);private static native int nativeAudioAbortPlayback(long handle);private static native boolean nativeAudioFailed(long handle);private static native int nativeAudioBeginAccess(long handle,int airMs,long seed,boolean responder,int ditherMs);private static native int nativeAudioAccessStep(long handle,float levelDb,boolean txAllowed);
}
