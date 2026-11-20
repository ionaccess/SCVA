package com.SCVA;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import com.SCVA.Utils.Utils;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.MessagesDataSource;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.db.SqliteHelper;
import com.SCVA.encryption.Aes256Encryptor;
import com.SCVA.models.Key;
import com.SCVA.models.TextMessage;
import com.SCVA.models.Configuration;
import java.util.ArrayList;
import java.util.List;

/** Test APK only. Run on an isolated, fresh emulator target application. */
public final class A050Instrumentation extends Instrumentation {
    private static final String TAG = "SCVA_A050_TEST";
    private static final String SECRET = "0123456789ABCDEF0123456789ABCDEF";
    private static final String PASSWORD = "synthetic-password";

    private static void check(boolean valid, String stage) {
        if (!valid) throw new AssertionError(stage);
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            runFreshDatabaseAndProviderChecks();
            runModem73VoxConfigurationChecks(getTargetContext());
            report.putString("result", "PASS");
            Log.i(TAG, "id=android_fresh_database_provider result=PASS");
            finish(0, report);
        } catch (Throwable failure) {
            report.putString("result", "FAIL");
            report.putString("stage", failure instanceof AssertionError ? failure.getMessage() : failure.getClass().getSimpleName());
            Log.e(TAG, "id=android_fresh_database_provider result=FAIL stage=" + report.getString("stage"));
            finish(1, report);
        }
    }

    private void runModem73VoxConfigurationChecks(android.content.Context app) {
        check(Configuration.DEFAULT_MODEM73_ENCRYPTION, "modem73_new_configuration_encryption_default");
        ConfigurationsDataSource configurations = new ConfigurationsDataSource(app);
        List<Configuration> prior = configurations.getAllConfigurations();
        boolean foundLegacyModem73 = false;
        for (Configuration existing : prior) {
            if ("MODEM73".equals(existing.getModulationType())) {
                foundLegacyModem73 = true;
                check(!existing.isModem73VoxEnabled() && existing.getModem73VoxLeadMs() == 550
                                && "SCVA".equals(existing.getModem73Callsign()) && existing.isEncryption()
                                && Configuration.MODEM73_PROFILE_DEFAULT.equals(existing.getModem73Profile()),
                        "legacy_modem73_vox_defaults");
            }
        }
        check(foundLegacyModem73, "version5_modem73_fixture_survived_migration");
        Configuration emptyCallsign = new Configuration(); emptyCallsign.setModem73Callsign(" ");
        check("SCVA".equals(emptyCallsign.getModem73Callsign()), "unset_modem73_callsign_defaults_to_SCVA");
        Configuration first = new Configuration();
        first.setName("A056 VOX on"); first.setModulationType("MODEM73"); first.setModem73Callsign("N0CALL");
        first.setModem73VoxEnabled(true); first.setModem73VoxLeadMs(725);
        first.setModem73Profile(Configuration.MODEM73_PROFILE_ROBUST);
        long firstId = configurations.create(first); check(firstId > 0, "create_vox_configuration");
        Configuration second = new Configuration();
        second.setName("A056 VOX off"); second.setModulationType("MODEM73"); second.setModem73Callsign("N0CALL");
        second.setModem73VoxEnabled(false); second.setModem73VoxLeadMs(1100);
        second.setModem73Profile(Configuration.MODEM73_PROFILE_REPEATED);
        long secondId = configurations.create(second); check(secondId > 0, "create_non_vox_configuration");
        ConfigurationsDataSource reloaded = new ConfigurationsDataSource(app);
        Configuration loadedFirst = reloaded.getConfiguration((int) firstId);
        Configuration loadedSecond = reloaded.getConfiguration((int) secondId);
        check(loadedFirst.isModem73VoxEnabled() && loadedFirst.getModem73VoxLeadMs() == 725,
                "reload_vox_configuration");
        check(Configuration.MODEM73_PROFILE_ROBUST.equals(loadedFirst.getModem73Profile())
                        && Configuration.MODEM73_PROFILE_REPEATED.equals(loadedSecond.getModem73Profile()),
                "profile_saved_independently");
        String[] verifiedProfiles = { Configuration.MODEM73_PROFILE_DEFAULT, Configuration.MODEM73_PROFILE_ROBUST,
                Configuration.MODEM73_PROFILE_REPEATED, Configuration.MODEM73_PROFILE_QPSK_14_NORMAL,
                Configuration.MODEM73_PROFILE_BPSK_14_NORMAL, Configuration.MODEM73_PROFILE_RDM_600,
                Configuration.MODEM73_PROFILE_RDMN_300 };
        long[] profileIds = new long[verifiedProfiles.length];
        for (int i = 0; i < verifiedProfiles.length; i++) {
            Configuration profileConfig = new Configuration(); profileConfig.setName("A057 profile " + i);
            profileConfig.setModulationType("MODEM73"); profileConfig.setModem73Profile(verifiedProfiles[i]);
            profileIds[i] = configurations.create(profileConfig); check(profileIds[i] > 0, "create_verified_profile_" + i);
        }
        ConfigurationsDataSource profileReload = new ConfigurationsDataSource(app);
        for (int i = 0; i < verifiedProfiles.length; i++) {
            Configuration restored = profileReload.getConfiguration((int) profileIds[i]);
            check(verifiedProfiles[i].equals(restored.getModem73Profile()), "reload_verified_profile_" + i);
            check(Modem73Backend.isSupportedProfile(verifiedProfiles[i]) && Modem73Backend.nativeProfile(verifiedProfiles[i]) > 0,
                    "profile_selection_mapping_" + i);
        }
        check(!loadedSecond.isModem73VoxEnabled() && loadedSecond.getModem73VoxLeadMs() == 1100,
                "independent_configuration_values");
        loadedFirst.setModem73VoxLeadMs(900); check(reloaded.update(loadedFirst), "edit_vox_configuration");
        Configuration edited = new ConfigurationsDataSource(app).getConfiguration((int) firstId);
        check(edited.isModem73VoxEnabled() && edited.getModem73VoxLeadMs() == 900
                        && Configuration.MODEM73_PROFILE_ROBUST.equals(edited.getModem73Profile()),
                "edited_configuration_persists");
        BackendSelection selection = new BackendSelection(1001, edited);
        check(selection.modem73VoxEnabled() && selection.modem73VoxLeadMs() == 900
                && selection.configuration().isModem73VoxEnabled()
                && selection.sameAs(new BackendSelection(1001, edited)), "effective_vox_selection");
        BackendSelection changed = new BackendSelection(1001, loadedSecond);
        check(!selection.sameAs(changed), "backend_reconfiguration_on_vox_change");
        Configuration defaultProfile = edited; defaultProfile.setModem73Profile(Configuration.MODEM73_PROFILE_DEFAULT);
        check(!selection.sameAs(new BackendSelection(1001, defaultProfile)), "backend_reconfiguration_on_profile_change");
        Log.i(TAG, "id=A057_modem73_profile_configuration result=PASS migrationDefaults=true profileCount=" + verifiedProfiles.length + " createReloadEdit=true independent=true immutableSelection=true");
    }

    private void runFreshDatabaseAndProviderChecks() {
        android.content.Context app = getTargetContext();
        // The test never clears an existing database. Its caller must use a fresh emulator/test identity.
        check(!app.getDatabasePath("data.db").exists(), "target_database_must_be_fresh");
        seedLegacyModem73Configuration(app);
        MessagesDataSource messages = new MessagesDataSource(app);
        messages.requireMessageMetadataSchema();
        KeysDataSource keys = new KeysDataSource(app);
        String wrapped = new Aes256Encryptor(new Utils().generateMd5(PASSWORD)).encrypt(SECRET);
        Key key = new Key();
        key.setName("synthetic-A050");
        key.setCreatedOn("0");
        key.setUserGroupId(1001);
        key.setKeyText(wrapped);
        keys.create(key);
        check(SECRET.equals(key.getPlainKey(PASSWORD)), "password_provider_unlock");
        List<EncryptedMessageHistory.KeyRef> refs = new ArrayList<>();
        refs.add(new EncryptedMessageHistory.KeyRef(key.getId(), 1001, key.getKeyText()));
        EncryptedMessageHistory.Session history = new EncryptedMessageHistory.Session();
        long token = history.begin(1001);
        check(history.unlock(token, refs.get(0), key.getPlainKey(PASSWORD), refs), "scoped_unlock");
        String[] algorithms = {"aes", "rc6", "twofish", "gost28147"};
        for (String algorithm : algorithms) {
            String input = "Android provider " + algorithm + " مرحبا 😀  ";
            byte[] wire = Modem73Crypto.prepare("N0CALL", algorithm, SECRET, input);
            Modem73Packet.Received parsed = Modem73Packet.receive(wire);
            check(parsed.encrypted && algorithm.equals(parsed.algorithm), "record_parse_" + algorithm);
            TextMessage row = new TextMessage();
            row.setType(TextMessage.HIS_TEXT);
            row.setDate("1");
            row.setMessage(parsed.stored);
            MessageEncryptionMetadata.modem73(1001, key.getId(), algorithm).applyTo(row);
            messages.create(row);
            TextMessage loaded = messages.getUserMessages(1001).get(messages.getUserMessages(1001).size() - 1);
            check(loaded.getKeyId() == key.getId() && algorithm.equals(loaded.getEncryptionAlgorithm())
                    && MessageEncryptionMetadata.MODEM73.equals(loaded.getCiphertextFormat())
                    && loaded.getCiphertextVersion() == 1 && loaded.isEncrypted(), "metadata_db_" + algorithm);
            Parcel parcel = Parcel.obtain();
            loaded.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            TextMessage copied = TextMessage.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            check(algorithm.equals(copied.getEncryptionAlgorithm()) && copied.getKeyId() == loaded.getKeyId(), "parcel_" + algorithm);
            EncryptedMessageHistory.Row view = new EncryptedMessageHistory.Row(copied.getId(), copied.getContactId(), copied.getKeyId(),
                    copied.isEncrypted(), copied.getMessage(), copied.getEncryptionAlgorithm(), copied.getCiphertextFormat(),
                    copied.getCiphertextVersion(), copied.getEncryptionParameters());
            check(history.display(view, refs).display().equals(input), "history_" + algorithm);
            check(!history.selectLegacyAlgorithm(token, view, "rc6") && !history.selectKey(token, view, refs.get(0), refs),
                    "no_per_row_choice_" + algorithm);
            check(messages.getUserMessages(1001).get(messages.getUserMessages(1001).size() - 1).getMessage().equals(parsed.stored),
                    "ciphertext_immutable_" + algorithm);
            Log.i(TAG, "id=android_metadata_history backend=MODEM73 algorithm=" + algorithm + " result=PASS");
        }
        byte[] prepared = "hello§".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<byte[]> shortPackets = GgWavePacket.packetizeText(prepared, false);
        check(shortPackets.size() == 1, "ggwave_short_packet_count");
        GgWavePacket.ReceiveResult shortResult = GgWavePacket.acceptReceived(shortPackets.get(0),
                new GgWavePacket.Reassembler(), android.os.SystemClock.elapsedRealtime());
        check(shortResult.message != null && java.util.Arrays.equals(prepared, shortResult.message.bytes()),
                "ggwave_short_java_parser");
        byte[] longPrepared = ("GGWave long " + java.util.Collections.nCopies(90, "x").toString() + "§")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<byte[]> longPackets = GgWavePacket.packetizeText(longPrepared, false);
        check(longPackets.size() > 1, "ggwave_fragment_count");
        GgWavePacket.Reassembler assembly = new GgWavePacket.Reassembler();
        GgWavePacket.ReceiveResult complete = null;
        long now = android.os.SystemClock.elapsedRealtime();
        for (byte[] packet : longPackets) {
            GgWavePacket.ReceiveResult next = GgWavePacket.acceptReceived(packet, assembly, now++);
            if (next.message != null) complete = next;
        }
        check(complete != null && java.util.Arrays.equals(longPrepared, complete.message.bytes()),
                "ggwave_long_java_reassembly");
        Log.i(TAG, "id=android_ggwave_java_packet_parser result=PASS fragments=" + longPackets.size());
        TextMessage plain = new TextMessage();
        plain.setType(TextMessage.HIS_TEXT);
        plain.setDate("2");
        plain.setMessage("SCM73E1: literal §¢\n  ");
        MessageEncryptionMetadata.plaintext(1001, 0).applyTo(plain);
        messages.create(plain);
        check(history.display(new EncryptedMessageHistory.Row(plain.getId(), 1001, 0, false, plain.getMessage(),
                plain.getEncryptionAlgorithm(), plain.getCiphertextFormat(), plain.getCiphertextVersion(), plain.getEncryptionParameters()), refs)
                .display().equals(plain.getMessage()), "literal_plaintext");
        try { keys.delete(key); throw new AssertionError("referenced_key_deletion_accepted"); }
        catch (KeysDataSource.KeyHistoryPolicyException expected) { }
        check(messages.getUserMessages(1001).size() == algorithms.length + 1, "row_count");
    }

    private void seedLegacyModem73Configuration(android.content.Context app) {
        new SqliteHelper(app); // Copies the bundled version-5 fixture without opening a DAO.
        android.database.sqlite.SQLiteDatabase database = android.database.sqlite.SQLiteDatabase.openDatabase(
                app.getDatabasePath("data.db").getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
        try {
            android.content.ContentValues legacy = new android.content.ContentValues();
            legacy.put("name", "MODEM73"); legacy.put("contactId", 1001);
            legacy.put("description", "pre-VOX seeded default configuration"); legacy.put("modulationType", "MODEM73");
            legacy.put("isDefault", 1); legacy.put("encryption", 0);
            long row = database.insertOrThrow("tblConfigs", null, legacy);
            check(row > 0 && database.getVersion() == 5, "seed_version5_modem73_configuration");
        } finally { database.close(); }
    }
}
