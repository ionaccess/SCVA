#include "Modem73ChannelAccess.h"

#include <algorithm>
#include <new>

bool Modem73ChannelAccess::begin(int frameAirMs, uint32_t seed, bool responder, int responderDitherMs)
{
	CsmaConfig config;
	config.threshold_db = -30.0f;
	config.sync_only = false;
	config.poll_ms = 25;
	const int airMs = std::max(1, frameAirMs);
	config.quiet_ms = std::max(300, std::min(3500, airMs / 4));
	config.cw = 8;
	config.slot_ms = 500;
	config.responder = responder;
	config.responder_quiet_ms = 300;
	config.responder_dither_ms = std::max(0, std::min(249, responderDitherMs));
	config.deaf_limit_ms = 5000;
	config.busy_limit_ms = std::max(30000, 8 * airMs);
	config.idle_credit_ms = 0;
	config.cold_channel_ms = 10000;
	config.dcd_detect_ms = 550;
	gate_.reset(new (std::nothrow) CsmaGate(config, seed));
	return static_cast<bool>(gate_);
}

int Modem73ChannelAccess::step(float levelDb, bool captureAlive, bool txAllowed)
{
	if (!gate_) return BUSY_TIMEOUT;
	if (gate_->step(levelDb, captureAlive, txAllowed) != CsmaGate::Verdict::TRANSMIT) return WAIT;
	switch (gate_->reason())
	{
	case CsmaGate::Reason::CLEAR:
	case CsmaGate::Reason::RESPONDER: return GRANTED;
	case CsmaGate::Reason::NO_AUDIO: return NO_CAPTURE;
	case CsmaGate::Reason::BUSY_OVERRIDE:
	default: return BUSY_TIMEOUT;
	}
}

int Modem73ChannelAccess::quietNeededMs() const { return gate_ ? gate_->quiet_needed_ms() : 0; }
int Modem73ChannelAccess::contentionLeftMs() const { return gate_ ? gate_->contention_left_ms() : 0; }
