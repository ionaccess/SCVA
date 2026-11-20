#pragma once

#include <cstdint>
#include <memory>

#include "csma.hh"

/** Narrow adapter over the pinned MODEM73 CsmaGate implementation. */
class Modem73ChannelAccess
{
public:
	enum Result { WAIT = 0, GRANTED = 1, BUSY_TIMEOUT = 2, NO_CAPTURE = 3 };
	bool begin(int frameAirMs, uint32_t seed, bool responder, int responderDitherMs);
	int step(float levelDb, bool captureAlive, bool txAllowed);
	int quietNeededMs() const;
	int contentionLeftMs() const;
private:
	std::unique_ptr<CsmaGate> gate_;
};
