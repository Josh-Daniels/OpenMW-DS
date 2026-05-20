#include "BackTrace.h"

#include <spdlog/spdlog.h>
#include <android/log.h>
#include <sys/system_properties.h>


void crash_log(const char* format, ...)
{
	va_list args;
	va_start(args, format);

	char buffer[1024];
	vsnprintf(buffer, sizeof(buffer), format, args);

	va_end(args);

	spdlog::error("{}", buffer);
}

void logDeviceInfo()
{
	// Use properties to log device and ABI information
	char device[PROP_VALUE_MAX];
	char abi[PROP_VALUE_MAX];
	char version[PROP_VALUE_MAX];
	__system_property_get("ro.product.model", device);
	__system_property_get("ro.product.cpu.abi", abi);
	__system_property_get("ro.build.version.release", version);

	crash_log("Device: %s", device);
	crash_log("ABI: %s", abi);
	crash_log("Android Version: %s", version);
}

void logLastInfo()
{
	crash_log(" ");
	crash_log(" ");
}

void dump_register(int signal, siginfo_t* info, void* ctx)
{
	static const std::array<std::string, 12> signal_name = {
			"SIGHUP", "SIGINT", "SIGQUIT", "SIGILL", "SIGTRAP", "SIGABRT",
			"SIGIOT", "SIGBUS", "SIGFPE", "SIGKILL", "SIGUSR1", "SIGSEGV"};

	crash_log(" ");
	crash_log("========== Alpha3 Crashed ==========");

	logDeviceInfo();
	logLastInfo();

	std::string si_code_name = "UNKNOWN";
	if (info->si_code == SI_USER) {
		crash_log("Cause: Killed by user");
		si_code_name = "SI_USER";
	}

	if (signal == SIGSEGV) {
		if (info->si_code == SEGV_MAPERR) {
			crash_log("Cause: De-referencing a null pointer");
			si_code_name = "SEGV_MAPERR";
		}
		else if (info->si_code == SEGV_ACCERR) {
			crash_log("Cause: Read-only pointer");
			si_code_name = "SEGV_ACCERR";
		}
	}

	auto context = reinterpret_cast<ucontext*>(ctx);

	crash_log("Signal: %d (%s)", signal, signal_name[signal].c_str());
	crash_log("Code: %d (%s)", info->si_code, si_code_name.c_str());
	crash_log("Fault Address: 0x%X", context->uc_mcontext.fault_address);

	crash_log(" ");
	crash_log("Register States:");

#ifdef __aarch64__
	for (int i = 0; i < 31; ++i) {
		crash_log("\tx%d: 0x%X", i, context->uc_mcontext.regs[i]);
	}
	crash_log("\tSP: 0x%X, PC: 0x%X", context->uc_mcontext.sp, context->uc_mcontext.pc);
#else
	crash_log("\tr0: 0x%X, r1: 0x%X, r2: 0x%X, r3: 0x%X",
			context->uc_mcontext.arm_r0, context->uc_mcontext.arm_r1,
			context->uc_mcontext.arm_r2, context->uc_mcontext.arm_r3);
	crash_log("\tr4: 0x%X, r5: 0x%X, r6: 0x%X, r7: 0x%X",
			context->uc_mcontext.arm_r4, context->uc_mcontext.arm_r5,
			context->uc_mcontext.arm_r6, context->uc_mcontext.arm_r7);
	crash_log("\tSP: 0x%X, LR: 0x%X, PC: 0x%X",
			context->uc_mcontext.arm_sp, context->uc_mcontext.arm_lr, context->uc_mcontext.arm_pc);
#endif
}

struct BacktraceState {
	void** current;
	void** end;
};

_Unwind_Reason_Code unwind_callback(struct _Unwind_Context* context, void* arg)
{
	auto state = reinterpret_cast<BacktraceState*>(arg);
	uintptr_t pc = _Unwind_GetIP(context);  // Get the instruction pointer
	if (pc) {
		if (state->current == state->end) {
			return _URC_END_OF_STACK;  // End of stack trace
		}
		else {
			*state->current++ = reinterpret_cast<void*>(pc);
		}
	}
	return _URC_NO_REASON;
}

void dump_stack(int ignoreDepth, int maxDepth)
{
	crash_log(" ");
	crash_log("BackTrace:");

	const int max = 100;
	void* buffer[max];

	BacktraceState state{};
	state.current = buffer;
	state.end = buffer + max;

	_Unwind_Backtrace(unwind_callback, &state);

	int count = (int) (state.current - buffer);
	if (count > maxDepth) {
		count = maxDepth + 1;
	}

	for (int idx = ignoreDepth; idx < count; idx++) {
		const void* addr = buffer[idx];
		const char* symbol = "";

		Dl_info info;
		if (dladdr(addr, &info) && info.dli_sname) {
			symbol = info.dli_sname;
		}

		int status{0};
		char* demangled = __cxxabiv1::__cxa_demangle(symbol, nullptr, nullptr, &status);

		crash_log("\t#%d [%s:0x%X] + 0x%X | [%s+%d]",
				(idx - ignoreDepth),
				info.dli_fname ? info.dli_fname : "unknown file",
				(uintptr_t) info.dli_fbase,
				(uintptr_t) addr - (uintptr_t) info.dli_fbase,
				info.dli_sname ? (status == 0 ? demangled : info.dli_sname) : "unknown symbol",
				(uintptr_t) addr - (uintptr_t) info.dli_saddr);

		if (demangled) {
			free(demangled);
		}
	}
}
