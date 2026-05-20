#pragma once

#include <cxxabi.h>
#include <unwind.h>
#include <array>
#include <dlfcn.h>
#include <asm-generic/siginfo.h>

void dump_register(int signal, siginfo_t* info, void* ctx);
void dump_stack(int ignoreDepth = 0, int maxDepth = 31);
