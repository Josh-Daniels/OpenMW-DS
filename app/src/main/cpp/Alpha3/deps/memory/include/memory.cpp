
#include "Memory.h"
#include "spdlog/spdlog.h"

namespace memory {

}

namespace Memory {
    std::unordered_map<uintptr_t, std::vector<uint8_t>> retOrigBytesMap;
    std::unordered_map<uintptr_t, std::vector<uint8_t>> nopOrigBytesMap;
}
