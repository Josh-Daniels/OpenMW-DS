#pragma once

#include "Gloss.h"
#include "spdlog/spdlog.h"
#include "SymUtils.h"

extern SymUtils* g_libEngine;
extern SymUtils* g_libGameUI;
extern SymUtils* g_libServerBrowser;
extern SymUtils* g_libMaterialSystem;

#define DECL_HOOK(_ret, _name, ...) \
    _ret (*_name)(__VA_ARGS__);     \
    _ret _name##_hook(__VA_ARGS__)

#define HOOK_ENGINE(_sym, _name) \
    GlossHook(g_libEngine->GetSymbol(_sym), (void*)(_name##_hook), (void**)(&_name))

#define HOOK_ENGINE_ADDR(_addr, _name) \
    GlossHookAddr((void*)g_libEngine->Abs((uintptr_t)_addr), (void*)(_name##_hook), (void**)(&_name), false, $ARM64)
#define HOOK_ENGINE_ADDR2(_addr, _name) \
    GlossHookAddr((void*)g_libEngine->Abs((uintptr_t)_addr), (void*)(_name##_hook), (void**)(&_name), true, $ARM64)

#define HOOK_GAMEUI_ADDR(_addr, _name) \
    GlossHookAddr((void*)g_libGameUI->Abs((uintptr_t)_addr), (void*)(_name##_hook), (void**)(&_name), false, $ARM64)

#define HOOK_SERVERBROWSER_ADDR(_addr, _name) \
    GlossHookAddr((void*)g_libServerBrowser->Abs((uintptr_t)_addr), (void*)(_name##_hook), (void**)(&_name), false, $ARM64)

#define HOOK_MATERIALSYSTEM_ADDR(_addr, _name) \
    GlossHookAddr((void*)g_libMaterialSystem->Abs((uintptr_t)_addr), (void*)(_name##_hook), (void**)(&_name), false, $ARM64)


namespace Memory {

    extern std::unordered_map<uintptr_t, std::vector<uint8_t>> nopOrigBytesMap;
    extern std::unordered_map<uintptr_t, std::vector<uint8_t>> retOrigBytesMap;

    static std::string buffer2Str(const std::vector<uint8_t>& buffer) {
        std::string bufferContents;
        for (size_t i = 0; i < buffer.size(); ++i) {
            char byteStr[4];
            snprintf(byteStr, sizeof(byteStr), "%02x ", buffer[i]);
            bufferContents += byteStr;
        }
        return bufferContents;
    }

    inline static uintptr_t getLibAddr(const char* libName) {
        return GlossGetLibBias(libName);
    }

    inline static GHandle getLibHandle(const char* libName) {
        return GlossOpen(libName);
    }

    static void unProtect(void* addr, size_t size = sizeof(uintptr_t)) {
        Unprotect((uintptr_t)addr, size);
    }

     inline static void readMemory(uintptr_t dest, uintptr_t src, size_t len) {
         ReadMemory((void*)dest, (void*)src, len, true);
     }

     inline static void writeMemory(uintptr_t dest, uintptr_t src, size_t len) {
         WriteMemory((void*)dest ,(void*)src, len, true);
     }

    static void gotHook(uintptr_t addr, uintptr_t hook_func, uintptr_t *orig_func = nullptr) {
        GlossGotHook((void*)addr, (void*)hook_func, (void**)orig_func);
    }

    inline static void* hook(const char* symbol, void* hook_func, void* *orig_func) {
        return GlossHook(g_libEngine->GetSymbol(symbol), hook_func, orig_func);
    }

    inline static void hook(void* addr, void* hook_func, void* *orig_func, bool isShortFunc = false) {
#ifdef __aarch64__
        GlossHookAddr(addr, hook_func, orig_func, isShortFunc, $ARM64);
#else
        GlossHookAddr((void*)(addr), (void*)(hook_func), (void**)(orig_func), isShortFunc , $THUMB);
#endif

    }

    // Hook a single call(branch) of a function
    static void hookBL(uintptr_t addr, uintptr_t hook_func, uintptr_t *orig_func) {
        #ifdef __aarch64__
                GlossHookBranchBL((void*)(addr), (void*)(hook_func), (void**)(orig_func), $ARM64);
        #else
                GlossHookBranchBL((void*)(addr), (void*)(hook_func), (void**)(orig_func), $THUMB);
        #endif

    }

    static void saveBytesToMap(uintptr_t addr, uint32_t byte_count, std::unordered_map<uintptr_t, std::vector<uint8_t>> &map) {
        std::vector<uint8_t> buffer(byte_count);
        if (map.find(addr) == map.end()) {
            readMemory(addr, reinterpret_cast<uintptr_t>(buffer.data()), byte_count);
            spdlog::info("saveBytesToMap addr: 0x{:x} bytes: {}", addr, buffer2Str(buffer));
            map[addr] = buffer;
        }
    }

    inline static void nop(uintptr_t addr, unsigned int word_count) {
        unsigned int byte_count = word_count * 2;
        saveBytesToMap(addr, byte_count, nopOrigBytesMap);
//        memory::patch::make_nop((void*)addr, byte_count); // todo this is not work
        #ifdef __aarch64__
                Gloss::Inst::MakeArm64NOP(addr, byte_count); // have to use gloss
        #else
                Gloss::Inst::MakeThumb16NOP(addr, byte_count);
        #endif
    }


    inline static void cancelNop(uintptr_t addr) {
        auto it = nopOrigBytesMap.find(addr);
        if (it != nopOrigBytesMap.end()) {
            Memory::writeMemory(addr, reinterpret_cast<uintptr_t>(it->second.data()), it->second.size());
        }
    }

    inline static void ret(uintptr_t addr) {
        unsigned int byte_count = sizeof(void*) / 2;
        saveBytesToMap(addr, byte_count, retOrigBytesMap);
        #ifdef __aarch64__
                Gloss::Inst::MakeArm64RET(addr, 0); // 0xD65F03C0; //RET (4 byte)
        #else
                Gloss::Inst::MakeThumbRET(addr, 1); // 0x46F7; //MOV PC, LR (2 byte)
        #endif

    }

    inline static void cancelRet(uintptr_t addr) {
        auto it = retOrigBytesMap.find(addr);
        if (it != retOrigBytesMap.end()) {
//            spdlog::info("cancelRet addr 0x{:x}", addr);
            Memory::writeMemory(addr, reinterpret_cast<uintptr_t>(it->second.data()), it->second.size());
        }
    }

};

namespace memory {
    template<typename Ret = void, typename... Args>
    [[nodiscard]] constexpr Ret call_function(void* address, Args... args)
    {
        if (!address) {
            spdlog::info("call_function: Invalid address");
            return Ret();
        }

        return reinterpret_cast<Ret(*)(Args...)>(address)(args...);
    }


    template<typename Ret>
    [[nodiscard]] constexpr Ret get_value_from_pointer(void* address)
    {
        if (!address) {
            return Ret{};
        }

        return *(Ret*) (address);
    }

    template<typename T>
    constexpr bool set_value_to_pointer(void* address, T value)
    {
        if (!address) {
            return false;
        }

        *(T*) (address) = value;
        return true;
    }
}

namespace memory {

}