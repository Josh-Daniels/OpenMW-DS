//
// Created by expvintl on 24-8-21.
//
#ifndef SAMP_SYMUTILS_H
#define SAMP_SYMUTILS_H

class SymUtils {

public:
    SymUtils() = default;
    ~SymUtils() = default;
private:
    std::unordered_map<const char*,uintptr_t> cached_symbols;
public:
    GHandle handle = nullptr;
    uintptr_t libAddr = 0;
    char libName[250];
public:
    inline GHandle Open(const char *libName) {
        if (!handle) handle = GlossOpen(libName);
        sprintf(this->libName, "%s", libName);
        if(!handle){
            spdlog::error("{} 打开失败!",libName);
        }
        return handle;
    }
    inline uintptr_t GetLibAddr() {
        if (!libAddr) libAddr = GlossFindLibMapping(libName, getpid(), nullptr, nullptr);
        return libAddr;
    }
    inline uintptr_t Abs(uintptr_t addr){
        return GetLibAddr() + addr;
    }
    inline uintptr_t ToLibAddr(uintptr_t addr){
        return addr - GetLibAddr();
    }
    template<typename T=void*>
    inline T GetSymbol(const char* name){
        //如果存在则直接返回
        if(cached_symbols.contains(name)) return (T)cached_symbols[name];
        if(!handle){
            spdlog::error("获取 {} 时lib没有打开!",name);
            return 0;
        }
        uintptr_t symbolAddr=GlossSymbol(handle, name);
        if(!symbolAddr){
            spdlog::error("无法找到符号 {} !", name);
            return 0;
        }
        //缓存符号
        cached_symbols.emplace(name,symbolAddr);
        return (T)symbolAddr;
    }

    template<typename Ret = void*>
    inline Ret GetVmtFunction(const char* symbolName,int index,bool isPtr=false){
        if(index<1){ //防止小于1
            index=1;
        }
        auto address= GetSymbol<uintptr_t>(symbolName);
        if (!address) {
            spdlog::error("{} 获取到了无效地址!",symbolName);
            return Ret();
        }
        //加1为了解决 从符号到第一个函数需要+2*8
        int offset=(index+1)*(int)sizeof(void*); //函数索引偏移
        return isPtr?(Ret)(address+offset):*(Ret*)(address+offset);
    }

    template<typename Ret = void*>
    inline Ret GetVmtFunction(uintptr_t address,int index,bool isPtr=false){
        if(index<0){ //防止小于0
            index=0;
        }
        if (!address) {
            spdlog::error("{} 是无效地址!",address);
            return Ret();
        }
        int offset=index*(int)sizeof(void*); //函数索引偏移
        return isPtr?(Ret)(address+offset):*(Ret*)(address+offset);//是否返回指针 既地址加偏移，否则解引用获取函数本体
    }
    template<typename Ret = void, typename... Args>
    inline Ret CallFunction(const char* symName, Args... args)
    {
        auto address= GetSymbol<uintptr_t>(symName);
        if (!address) {
            spdlog::error("{} 获取到了无效地址!",symName);
            return Ret();
        }
        return reinterpret_cast<Ret(*)(Args...)>(address)(args...);
    }
    template<typename Ret = void, typename... Args>
    inline Ret CallFunction(uintptr_t address, Args... args)
    {
        if (!address) {
            spdlog::error("{} 是无效地址!",address);
            return Ret();
        }
        return reinterpret_cast<Ret(*)(Args...)>(address)(args...);
    }
    template<typename Ret = void, typename... Args>
    inline Ret CallVmtFunction(uintptr_t address,int index, Args... args)
    {
        if (!address) {
            spdlog::error("{} 是无效地址!",address);
            return Ret();
        }
        auto vmtAddr=GetVmtFunction<uintptr_t>(address,index);
        if(!vmtAddr){
            spdlog::error("无法找到虚函数! {}",address);
            return Ret();
        }
        return reinterpret_cast<Ret(*)(Args...)>(vmtAddr)(args...);
    }
};

#endif //SAMP_SYMUTILS_H
