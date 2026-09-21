#ifndef SCVA_RS_DECODE_CHECKED_H
#define SCVA_RS_DECODE_CHECKED_H

#include <cstddef>
#include <vector>

namespace scva {
// Erasures are zero-based positions in this codeword, never RF-symbol indexes.
// A negative result leaves no payload available to the caller.
template<class Codec, class Byte>
int decodeChecked(const Codec& codec, std::vector<Byte>& word, int n, int k,
                  const std::vector<int>& erasures, std::vector<int>& positions) {
    positions.clear();
    if (n <= 0 || k <= 0 || k >= n || word.size() != static_cast<std::size_t>(n) ||
        erasures.size() > static_cast<std::size_t>(n - k)) {
        word.clear();
        return -1;
    }
    std::vector<bool> seen(n, false);
    for (int index : erasures) {
        if (index < 0 || index >= n || seen[index]) {
            word.clear();
            return -1;
        }
        seen[index] = true;
    }
    const int corrected = codec.decode(word, erasures, &positions);
    if (corrected < 0) {
        word.clear();
        positions.clear();
        return corrected;
    }
    word.resize(k);
    return corrected;
}
}

#endif
