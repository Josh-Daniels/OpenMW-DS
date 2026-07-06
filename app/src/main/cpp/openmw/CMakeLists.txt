cmake_minimum_required(VERSION 3.2.0)

# https://github.com/libjpeg-turbo/libjpeg-turbo/releases
set(LIBJPEG_TURBO_VERSION 3.1.0)
set(LIBJPEG_TURBO_HASH SHA256=9564c72b1dfd1d6fe6274c5f95a8d989b59854575d4bbee44ade7bc17aa9bc93)

set(LIBXML2_VERSION 2.14.3)
set(LIBXML2_HASH SHA256=e282bfeb750be5f4baf98195c6393f9b7beadc6bed4d85e7ca37a66f56394b5f)

# http://www.libpng.org/pub/png/libpng.html
set(LIBPNG_VERSION 1.6.48)
set(LIBPNG_HASH SHA256=46fd06ff37db1db64c0dc288d78a3f5efd23ad9ac41561193f983e20937ece03)

# https://www.freetype.org/index.html#news
set(FREETYPE2_VERSION 2.13.3)
set(FREETYPE2_HASH SHA256=5c3a8e78f7b24c20b25b54ee575d6daa40007a5f4eea2845861c3409b3021747)

# https://github.com/kcat/openal-soft/releases
set(OPENAL_VERSION 1.24.3)
set(OPENAL_HASH SHA256=7e1fecdeb45e7f78722b776c5cf30bd33934b961d7fd2a11e0494e064cc631ce)

# http://www.boost.org/users/download/
set(BOOST_VERSION 1.88.0)
set(BOOST_HASH SHA256=dcea50f40ba1ecfc448fdf886c0165cf3e525fef2c9e3e080b9804e8117b9694)

# https://www.ffmpeg.org/download.html
set(FFMPEG_VERSION 7.1.1)
set(FFMPEG_HASH SHA256=0c8da2f11579a01e014fc007cbacf5bb4da1d06afd0b43c7f8097ec7c0f143ba)

# https://www.libsdl.org/download-2.0.php
set(SDL2_VERSION 2.32.4)
set(SDL2_HASH SHA256=349268f695c02efbc9b9148a70b85e58cefbbf704abd3e91be654db7f1e2c863)

# https://github.com/bulletphysics/bullet3/releases
set(BULLET_VERSION 3.25)
set(BULLET_HASH SHA256=baa642c906576d4d98d041d0acb80d85dd6eff6e3c16a009b1abf1ccd2bc0a61)

# https://github.com/MyGUI/mygui/releases
set(MYGUI_VERSION 3.4.3)
set(MYGUI_HASH SHA256=d1d5f294670ae71f7200ed4b30859018281d8cfd45d6a38d18b97a4aba604c42)

# https://github.com/sisah2/ng_gl4es/releases
set(GL4ES_VERSION f7a8370852579d6a11154178929a02e0cf60d574)
set(GL4ES_HASH SHA256=b565e717c7d192e936bda25f3cb90ad8db398af56414ec08294b6716574c1a6d)

# https://github.com/openscenegraph/OpenSceneGraph/releases
set(OSG_VERSION 8aa8d91747ff414ab0de19aae1e0dbd739acea2c)
set(OSG_HASH SHA256=aea196550f02974d6d09291c5d83b51ca6a03b3767e234a8c0e21322927d1e12)

# https://github.com/lz4/lz4/releases
set(LZ4_VERSION 1.10.0)
set(LZ4_HASH SHA256=537512904744b35e232912055ccf8ec66d768639ff3abe5788d90d792ec5f48b)

# https://luajit.org/download.html
set(LUAJIT_VERSION 2.1.ROLLING)
set(LUAJIT_HASH SHA256=1ad2e34b111c802f9d0cdf019e986909123237a28c746b21295b63c9e785d9c3)

set(LIBICU_VERSION 70-1)
set(LIBICU_HASH SHA256=4013d7aff8a34bae410664bdb16554d7eda03654b22627b9284ab55a685492d4)

# https://github.com/sisah2/openmw/commits/clean
set(OPENMW_VERSION 6eae10346cb26bf1856e6b4368f986fb40054b76)
set(OPENMW_HASH SHA256=44d044ad0a5546216fe611fe92c37065bb07e2efeeeaac4f049dae03df313186)

project(openmw-android)

include(ExternalProject)
include(ProcessorCount)
ProcessorCount(NPROC)
if(NPROC EQUAL 0)
	set(NPROC 2)  # Fallback to 2 cores if detection fails
endif()

# Detect ABI and setup architecture-specific variables for the Gradle environment
if(NOT DEFINED ARCH)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(ARCH "arm64")
        set(NDK_TRIPLET "aarch64-linux-android")
        set(FFMPEG_CPU "armv8-a")
    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(ARCH "arm")
        set(NDK_TRIPLET "arm-linux-androideabi")
        set(FFMPEG_CPU "armv7-a")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(ARCH "x86_64")
        set(NDK_TRIPLET "x86_64-linux-android")
        set(FFMPEG_CPU "x86_64")
    elseif(ANDROID_ABI STREQUAL "x86")
        set(ARCH "x86")
        set(NDK_TRIPLET "i686-linux-android")
        set(FFMPEG_CPU "i686")
    endif()
endif()

if(NOT DEFINED ANDROID_API)
    set(ANDROID_API ${ANDROID_NATIVE_API_LEVEL})
endif()

if(NOT DEFINED LUAJIT_HOST_CC)
    if(APPLE)
        set(LUAJIT_HOST_CC "cc")
    else()
        set(LUAJIT_HOST_CC "gcc")
    endif()
endif()

# Find the host tag for NDK prebuilt tools
if(NOT DEFINED ANDROID_HOST_TAG)
    if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Linux")
        set(ANDROID_HOST_TAG "linux-x86_64")
    elseif(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
        set(ANDROID_HOST_TAG "darwin-x86_64")
    elseif(CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows")
        set(ANDROID_HOST_TAG "windows-x86_64")
    endif()
endif()

set(NDK_TOOLCHAIN_BIN "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}/bin")

find_program(CCACHE_FOUND ccache)
if(CCACHE_FOUND)
	message (STATUS "CCACHE found and is being used.")
    set(CC_WRAPPER "ccache ")
else()
	message (STATUS "CCACHE not found.")
    set(CC_WRAPPER "")
endif()

set(GLOBAL_CFLAGS "-w -fPIC -O3 -flto=thin")
set(GLOBAL_CXXFLAGS "-w -fPIC -O3 -frtti -fexceptions -flto=thin")
if(APPLE)
    set(GLOBAL_LDFLAGS "-w -fPIC -flto=thin -fuse-ld=lld -L${CMAKE_BINARY_DIR}/prefix/lib")
else()
    set(GLOBAL_LDFLAGS "-w -fPIC -Wl,--undefined-version -flto=thin -fuse-ld=lld -L${CMAKE_BINARY_DIR}/prefix/lib")
endif()

set(ndk_triplet ${NDK_TRIPLET})
set(download_dir $ENV{HOME}/.cache/openmw-android-downloads/)
set(prefix ${CMAKE_BINARY_DIR}/prefix/)
set(host ${ndk_triplet})
set(android_platform android-${ANDROID_API})
set(app_abi ${ANDROID_ABI})
set(jni_libs_dir "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
set(OPENMW_SRC "" CACHE PATH "Path to a local OpenMW source checkout")
get_filename_component(PROJECT_ROOT ${CMAKE_SOURCE_DIR}/../../.. ABSOLUTE)
message(STATUS "PROJECT_ROOT: ${APP_DIR}")

if(NOT DEFINED GENERATED_ASSETS_DIR)
	message(FATAL_ERROR "GENERATED_ASSETS_DIR not set (should be passed from Gradle)")
endif()

file(MAKE_DIRECTORY "${GENERATED_ASSETS_DIR}")
file(MAKE_DIRECTORY "${jni_libs_dir}")

set(wrapper_command ${CMAKE_BINARY_DIR}/command_wrapper.sh)
file(WRITE ${wrapper_command}
"#!/bin/bash
export PATH=\"${NDK_TOOLCHAIN_BIN}:\$PATH\"
export CC=\"${CC_WRAPPER}${NDK_TRIPLET}${ANDROID_API}-clang\"
export CXX=\"${CC_WRAPPER}${NDK_TRIPLET}${ANDROID_API}-clang++\"
export CFLAGS=\"${GLOBAL_CFLAGS}\"
export CXXFLAGS=\"${GLOBAL_CXXFLAGS}\"
export LDFLAGS=\"${GLOBAL_LDFLAGS}\"
export AR=\"llvm-ar\"
export RANLIB=\"llvm-ranlib\"
export STRIP=\"llvm-strip\"

# --- pkg-config paths ---
export PKG_CONFIG_LIBDIR=\"${prefix}/lib/pkgconfig:${prefix}/share/pkgconfig\"
export PKG_CONFIG_PATH=\"${prefix}/lib/pkgconfig:${prefix}/share/pkgconfig\"

exec \"\$@\"
")
execute_process(COMMAND chmod +x ${wrapper_command})

set(COMMON_CMAKE_ARGS
	-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
	-DANDROID_ABI=${app_abi}
	-DANDROID_PLATFORM=${android_platform}
	-DANDROID_STL=c++_shared
	-DANDROID_LD=deprecated
	-DANDROID_CPP_FEATURES=
	-DCMAKE_C_FLAGS=-I${prefix}/include/\ ${GLOBAL_CFLAGS}
	-DCMAKE_CXX_FLAGS=-I${prefix}/include/\ ${GLOBAL_CXXFLAGS}
	-DCMAKE_SHARED_LINKER_FLAGS=${GLOBAL_LDFLAGS}
	-DCMAKE_BUILD_TYPE=release #${CMAKE_BUILD_TYPE}
	-DCMAKE_DEBUG_POSTFIX=
	-DCMAKE_INSTALL_PREFIX=${prefix}
	-DCMAKE_FIND_ROOT_PATH=${prefix}
	-DHAVE_LD_VERSION_SCRIPT=OFF
)

if(CCACHE_FOUND)
	list(APPEND COMMON_CMAKE_ARGS
		-DCMAKE_C_COMPILER_LAUNCHER=ccache
		-DCMAKE_CXX_COMPILER_LAUNCHER=ccache
	)
endif()

set(COMMON_AUTOCONF_FLAGS
	--host=${host}
	--enable-static
	--disable-shared
	--prefix=${prefix}
)

ExternalProject_Add(zlib
	URL https://github.com/madler/zlib/archive/refs/tags/v1.3.1.tar.gz
	URL_HASH SHA256=17e88863f3600672ab49182f217281b6fc4d3c762bde361935e436a95214d05c
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(libjpeg-turbo
	URL https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/${LIBJPEG_TURBO_VERSION}/libjpeg-turbo-${LIBJPEG_TURBO_VERSION}.tar.gz
	URL_HASH ${LIBJPEG_TURBO_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DENABLE_SHARED=false

	BUILD_COMMAND ${wrapper_command} make -j${NPROC} PROGRAMS=
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(libpng
	URL http://prdownloads.sourceforge.net/libpng/libpng-${LIBPNG_VERSION}.tar.xz
	URL_HASH ${LIBPNG_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/configure
	${COMMON_AUTOCONF_FLAGS}

	BUILD_COMMAND ${wrapper_command} make check_PROGRAMS= bin_PROGRAMS=
	INSTALL_COMMAND ${wrapper_command} make install check_PROGRAMS= bin_PROGRAMS=
)

ExternalProject_Add(libicu-host
	URL https://github.com/unicode-org/icu/archive/refs/tags/release-${LIBICU_VERSION}.zip
	URL_HASH ${LIBICU_HASH}
	DOWNLOAD_DIR ${download_dir}
	LOG_CONFIGURE 1
	LOG_BUILD 1
	LOG_INSTALL 1

	CONFIGURE_COMMAND <SOURCE_DIR>/icu4c/source/configure
    --enable-static
    --disable-shared
    --disable-tests
    --disable-samples

	BUILD_COMMAND make -j${NPROC}
	INSTALL_COMMAND ""
)

ExternalProject_Get_Property(libicu-host SOURCE_DIR BINARY_DIR)

ExternalProject_Add(libicu
	DEPENDS libicu-host
	SOURCE_DIR ${SOURCE_DIR}
	DOWNLOAD_COMMAND ""

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/icu4c/source/configure
	${COMMON_AUTOCONF_FLAGS}
	--disable-tests
	--disable-samples
	--disable-icuio
	--disable-extras
	--with-cross-build=${BINARY_DIR}

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install install >> /dev/null
)

ExternalProject_Add(freetype2
	URL http://prdownloads.sourceforge.net/freetype/freetype-${FREETYPE2_VERSION}.tar.gz
	URL_HASH ${FREETYPE2_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_DISABLE_FIND_PACKAGE_ZLIB=TRUE
    -DCMAKE_DISABLE_FIND_PACKAGE_BZip2=TRUE
    -DCMAKE_DISABLE_FIND_PACKAGE_PNG=TRUE

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(openal
	URL https://github.com/kcat/openal-soft/archive/${OPENAL_VERSION}.tar.gz
	URL_HASH ${OPENAL_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5
	-DALSOFT_EXAMPLES=OFF
	-DALSOFT_TESTS=OFF
	-DALSOFT_UTILS=OFF
	-DALSOFT_NO_CONFIG_UTIL=ON
	-DALSOFT_BACKEND_OPENSL=ON
	-DALSOFT_BACKEND_WAVE=OFF

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
	COMMAND ${CMAKE_COMMAND} -E copy_if_different ${prefix}/lib/libopenal.so ${jni_libs_dir}/
)

ExternalProject_Add(boost
	URL https://github.com/boostorg/boost/releases/download/boost-${BOOST_VERSION}/boost-${BOOST_VERSION}-cmake.tar.gz
	URL_HASH ${BOOST_HASH}
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND patch -d <SOURCE_DIR>/libs/system/ -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/boost/system.diff
	COMMAND patch -d <SOURCE_DIR>/libs/regex/ -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/boost/regex.diff

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
    -DCMAKE_CXX_FLAGS=-I<SOURCE_DIR>/libs/core/include/\ ${GLOBAL_CXXFLAGS}
	-DBOOST_EXCLUDE_LIBRARIES=process

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install install >> /dev/null

	COMMAND ${wrapper_command} llvm-ranlib ${prefix}/lib/libboost_{filesystem,program_options,iostreams}.a
)

ExternalProject_Add(ffmpeg
	URL http://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.bz2
	URL_HASH ${FFMPEG_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/configure
	--disable-asm
    --disable-optimizations
	--target-os=android
	--enable-cross-compile
	--cross-prefix=llvm-
	--cc=${host}${ANDROID_API}-clang
	--arch=${ARCH}
	--cpu=${FFMPEG_CPU}
	--prefix=${prefix}
	--extra-cflags=${GLOBAL_CFLAGS}\ -fvisibility=hidden
	--extra-cxxflags=${GLOBAL_CXXFLAGS}\ -fvisibility=hidden
	--extra-ldflags=${GLOBAL_LDFLAGS}
	--enable-version3
	--enable-pic
	--disable-everything
	--disable-doc
	--disable-programs
	--disable-autodetect
	--disable-iconv
	--enable-decoder=mp3
	--enable-demuxer=mp3
	--enable-decoder=bink
	--enable-decoder=binkaudio_rdft
	--enable-decoder=binkaudio_dct
	--enable-demuxer=bink
	--enable-demuxer=wav
	--enable-decoder=pcm_*
	--enable-decoder=vp8
	--enable-decoder=vp9
	--enable-decoder=opus
	--enable-decoder=vorbis
	--enable-demuxer=matroska
	--enable-demuxer=ogg

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(sdl2
	URL https://www.libsdl.org/release/SDL2-${SDL2_VERSION}.tar.gz
	DOWNLOAD_DIR ${download_dir}
#	BUILD_ALWAYS 1

#	PATCH_COMMAND patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/sdl2-add-display-mode.patch

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DSDL_STATIC=OFF

	BUILD_COMMAND ${wrapper_command} make -j${NPROC} PROGRAMS=
	INSTALL_COMMAND ${wrapper_command} make install
	COMMAND ${CMAKE_COMMAND} -E copy_if_different ${prefix}/lib/libSDL2.so ${jni_libs_dir}/
)

ExternalProject_Add(bullet
	URL https://github.com/bulletphysics/bullet3/archive/${BULLET_VERSION}.tar.gz
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND ""

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DBUILD_BULLET2_DEMOS=OFF
	-DBUILD_CPU_DEMOS=OFF
	-DBUILD_UNIT_TESTS=OFF
	-DBUILD_EXTRAS=OFF
	-DUSE_DOUBLE_PRECISION=ON
	-DBULLET2_MULTITHREADING=ON
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(NG-GL4ES
	GIT_REPOSITORY https://github.com/sisah2/Ng-gl4es
	GIT_TAG Openmw3
	GIT_SUBMODULES_RECURSE ON
	DOWNLOAD_DIR ${download_dir}

	UPDATE_COMMAND ""

	PATCH_COMMAND sed -i -e "s/3.22.1/3.19.6/g" <SOURCE_DIR>/3rdparty/glslang/CMakeLists.txt
	COMMAND sed -i -e "s/3.22.1/3.19.6/g" <SOURCE_DIR>/3rdparty/SPIRV-Tools/CMakeLists.txt
	COMMAND sed -i -e "s/VERSION_GREATER_EQUAL/VERSION_LESS/g" <SOURCE_DIR>/3rdparty/SPIRV-Tools/CMakeLists.txt
	COMMAND sed -i -e "s/CMAKE_CXX_STANDARD 23/CMAKE_CXX_STANDARD 17/g" <SOURCE_DIR>/CMakeLists.txt

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
    -DSPIRV-Headers_SOURCE_DIR=<SOURCE_DIR>/3rdparty/SPIRV-headers
    -DINCLUDE_SPIRV_TOOLS=ON

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${CMAKE_COMMAND} -E make_directory ${prefix}/lib/
	COMMAND ${CMAKE_COMMAND} -E copy libng_gl4es.so ${prefix}/lib/
	COMMAND ${CMAKE_COMMAND} -E make_directory ${prefix}/include/gl4es/
	COMMAND ${CMAKE_COMMAND} -E copy_directory <SOURCE_DIR>/include ${prefix}/include/gl4es/
	COMMAND ${CMAKE_COMMAND} -E make_directory ${prefix}/include/
	COMMAND ${CMAKE_COMMAND} -E copy_directory <SOURCE_DIR>/include ${prefix}/include/
	COMMAND ${CMAKE_COMMAND} -E copy_if_different ${prefix}/lib/libng_gl4es.so ${jni_libs_dir}/
)

ExternalProject_Add(mygui
	DEPENDS freetype2

	URL https://github.com/MyGUI/mygui/archive/MyGUI${MYGUI_VERSION}.tar.gz
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND sed -i -e "s/unicode_char = uint32/unicode_char = char32_t/g" <SOURCE_DIR>/MyGUIEngine/include/MyGUI_UString.h
	COMMAND sed -i -e "s/code_point = uint16/code_point = char16_t/g" <SOURCE_DIR>/MyGUIEngine/include/MyGUI_UString.h

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DMYGUI_RENDERSYSTEM=1
	-DMYGUI_BUILD_DEMOS=OFF
	-DMYGUI_BUILD_TOOLS=OFF
	-DMYGUI_BUILD_PLUGINS=OFF
	-DMYGUI_DONT_USE_OBSOLETE=ON
	-DMYGUI_STATIC=ON

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(lz4
	URL https://github.com/lz4/lz4/archive/v${LZ4_VERSION}.tar.gz
	URL_HASH ${LZ4_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>/build/cmake/
	${COMMON_CMAKE_ARGS}
	-DBUILD_STATIC_LIBS=ON
	-DBUILD_SHARED_LIBS=OFF

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

set(LUAJIT_MAKE_FLAGS
    	HOST_CC=${LUAJIT_HOST_CC}
    	CFLAGS=
    	TARGET_CFLAGS=${GLOBAL_CFLAGS}
    	CROSS=llvm-
    	STATIC_CC=${host}${ANDROID_API}-clang
    	DYNAMIC_CC=${host}${ANDROID_API}-clang\ -fPIC
    	TARGET_LD=${host}${ANDROID_API}-clang
    	TARGET_SYS=Linux
    	PREFIX=${prefix}
    	BUILDMODE=static
    	XCFLAGS=-DLUAJIT_ENABLE_SANDBOX_BYPASS
    	LDFLAGS=
)

ExternalProject_Add(luajit
	URL https://github.com/DreamWeave-MP/rubic0n/archive/refs/tags/development.tar.gz
	DOWNLOAD_DIR ${download_dir}

	BUILD_IN_SOURCE 1

	CONFIGURE_COMMAND ""

	BUILD_COMMAND ${wrapper_command} make amalg
	${LUAJIT_MAKE_FLAGS}

	INSTALL_COMMAND ${wrapper_command} make install
	${LUAJIT_MAKE_FLAGS}
)

ExternalProject_Add(libxml
	DEPENDS zlib

	URL https://github.com/GNOME/libxml2/archive/refs/tags/v${LIBXML2_VERSION}.tar.gz
	URL_HASH ${LIBXML2_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DBUILD_SHARED_LIBS=OFF
	-DLIBXML2_WITH_CATALOG=OFF
	-DLIBXML2_WITH_ICONV=OFF
	-DLIBXML2_WITH_LZMA=OFF
	-DLIBXML2_WITH_PROGRAMS=OFF
	-DLIBXML2_WITH_PYTHON=OFF
	-DLIBXML2_WITH_TESTS=OFF
	-DLIBXML2_WITH_ZLIB=ON

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(libogg
	URL https://github.com/xiph/ogg/releases/download/v1.3.5/libogg-1.3.5.tar.gz
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(libvorbis
	DEPENDS libogg
	URL https://github.com/xiph/vorbis/releases/download/v1.3.7/libvorbis-1.3.7.tar.gz
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
)

ExternalProject_Add(uqm
	DEPENDS sdl2 openal libpng zlib libvorbis libogg

	GIT_REPOSITORY https://github.com/JHGuitarFreak/UQM-MegaMod.git
	GIT_TAG master
	DOWNLOAD_DIR ${download_dir}
#	BUILD_ALWAYS 1

	UPDATE_COMMAND ""

	PATCH_COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_SOURCE_DIR}/openmw/patches/uqm/jni_engine.c <SOURCE_DIR>/src/jni_engine.c
	COMMAND	${CMAKE_COMMAND} -E copy ${CMAKE_SOURCE_DIR}/openmw/patches/uqm/config_android.h.in <SOURCE_DIR>/src/config_android.h.in
	COMMAND sed -i "/add_library (UrQuanMasters SHARED/i list(APPEND UQM_SOURCES src/jni_engine.c)" <SOURCE_DIR>/CMakeLists.txt

	CONFIGURE_COMMAND ${wrapper_command} cmake -S <SOURCE_DIR> -B <BINARY_DIR>
	${COMMON_CMAKE_ARGS}

	BUILD_COMMAND ${wrapper_command} make -C <BINARY_DIR> -j${NPROC}
	INSTALL_COMMAND ${CMAKE_COMMAND} -E copy <BINARY_DIR>/libUrQuanMasters.so ${jni_libs_dir}/libuqm.so
)

ExternalProject_Add(dethrace
	DEPENDS sdl2 openal

	GIT_REPOSITORY https://github.com/Duron27-org/dethrace.git
	GIT_TAG main
	GIT_SUBMODULES_RECURSE ON
	DOWNLOAD_DIR ${download_dir}

	UPDATE_COMMAND ""

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5
	-DDETHRACE_ANDROID=ON
	-DDETHRACE_PLATFORM_SDL2=ON
	-DSDL2_INCLUDE_DIR=${prefix}/include
	-DSDL2_LIBRARY=${prefix}/lib/libSDL2.so

	BUILD_COMMAND ${wrapper_command} make -C <BINARY_DIR> -j${NPROC}
	INSTALL_COMMAND ${CMAKE_COMMAND} -E copy <BINARY_DIR>/src/DETHRACE/libdethrace.so ${jni_libs_dir}/
)

set(LIBCOLLADA_PATCH
	patch -d <SOURCE_DIR> -p0 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/libcollada-minizip-fix.patch
)

ExternalProject_Add(libcollada
	DEPENDS libxml boost

	URL https://github.com/rdiankov/collada-dom/archive/v2.5.0.tar.gz
	URL_HASH SHA256=3be672407a7aef60b64ce4b39704b32816b0b28f61ebffd4fbd02c8012901e0d
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND ${LIBCOLLADA_PATCH}
	COMMAND sed -i -e "s|boost/filesystem/convenience.hpp|boost/filesystem.hpp|g" <SOURCE_DIR>/dom/include/dae.h
	COMMAND sed -i -e "s|boost/filesystem/convenience.hpp|boost/filesystem.hpp|g" <SOURCE_DIR>/dom/src/dae/daeUtils.cpp
	COMMAND sed -i -e "s/branch_path/parent_path/g" <SOURCE_DIR>/dom/src/dae/daeZAEUncompressHandler.cpp

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DCMAKE_POLICY_VERSION_MINIMUM=3.5
	-DCMAKE_CXX_FLAGS=-Dauto_ptr=unique_ptr\ ${GLOBAL_CXXFLAGS}

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}
	INSTALL_COMMAND ${wrapper_command} make install
	COMMAND ${CMAKE_COMMAND} -E copy_if_different ${prefix}/lib/libcollada-dom2.5-dp.so ${jni_libs_dir}/
)

set(OSG_COMMON
	-DOPENGL_PROFILE="GL2"
	-DDYNAMIC_OPENTHREADS=OFF
	-DDYNAMIC_OPENSCENEGRAPH=OFF
	-DBUILD_OSG_PLUGIN_OSG=ON
	-DBUILD_OSG_PLUGIN_DAE=ON
	-DBUILD_OSG_PLUGIN_DDS=ON
	-DBUILD_OSG_PLUGIN_KTX=ON
	-DBUILD_OSG_PLUGIN_TGA=ON
	-DBUILD_OSG_PLUGIN_BMP=ON
	-DBUILD_OSG_PLUGIN_JPEG=ON
	-DBUILD_OSG_PLUGIN_PNG=ON
	-DBUILD_OSG_PLUGIN_FREETYPE=ON
	-DJPEG_INCLUDE_DIR=${prefix}/include/
	-DPNG_INCLUDE_DIR=${prefix}/include/
	-DFREETYPE_DIR=${prefix}
    -DCOLLADA_INCLUDE_DIR=${prefix}/include/collada-dom2.5
	-DOSG_CPP_EXCEPTIONS_AVAILABLE=TRUE
	-DOSG_GL1_AVAILABLE=ON
	-DOSG_GL2_AVAILABLE=ON
	-DOSG_GL3_AVAILABLE=OFF
	-DOSG_GLES1_AVAILABLE=OFF
	-DOSG_GLES2_AVAILABLE=OFF
	-DOSG_GL_LIBRARY_STATIC=OFF
	-DOSG_GL_DISPLAYLISTS_AVAILABLE=OFF
	-DOSG_GL_MATRICES_AVAILABLE=ON
	-DOSG_GL_VERTEX_FUNCS_AVAILABLE=ON
	-DOSG_GL_VERTEX_ARRAY_FUNCS_AVAILABLE=ON
	-DOSG_GL_FIXED_FUNCTION_AVAILABLE=ON
	-DBUILD_OSG_APPLICATIONS=OFF
	-DBUILD_OSG_PLUGINS_BY_DEFAULT=OFF
    -DOPENGL_gl_LIBRARY=${prefix}/lib/libng_gl4es.so
)

set(OSG_PATCH
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/disable-polygon-offset.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/remove-lib-prefix-from-plugins.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/fix-freetype-include-dirs.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/0001-Replace-Atomic-impl-with-std-atomic.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/0002-BufferObject-make-numClients-atomic.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/0004-IncrementalCompileOperation-wrap-some-stuff-in-atomi.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/force-add-plugins.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/dae_collada.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/ng-gl4es.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/wtf.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/osg/0005-CullSettings-make-inheritanceMask-atomic-to-silence-.patch || true
)

ExternalProject_Add(osg
	DEPENDS libjpeg-turbo libpng NG-GL4ES freetype2 libcollada

	GIT_REPOSITORY https://github.com/OpenMW/osg
	GIT_TAG ${OSG_VERSION}
	DOWNLOAD_DIR ${download_dir}

	UPDATE_COMMAND ""

	PATCH_COMMAND ${OSG_PATCH}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	${OSG_COMMON}
	-DCMAKE_CXX_FLAGS=-Dauto_ptr=unique_ptr\ -I${prefix}/include\ ${GLOBAL_CXXFLAGS}

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}

	INSTALL_COMMAND ${CMAKE_COMMAND} -E make_directory <SOURCE_DIR>/3rdparty/build/libpng
	COMMAND ${CMAKE_COMMAND} -E make_directory <SOURCE_DIR>/3rdparty/build/libjpeg
	COMMAND ${CMAKE_COMMAND} -E make_directory <SOURCE_DIR>/3rdparty/build/freetype
	COMMAND ${wrapper_command} make install
)

ExternalProject_Add(kram
	URL https://github.com/Duron27/kram/archive/09bf2e9bd52f20658f032e957a75093c3be08698.tar.gz
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}

	BUILD_COMMAND ${wrapper_command} make -j${NPROC}

	INSTALL_COMMAND ${CMAKE_COMMAND} -E make_directory ${prefix}/lib/
	COMMAND ${CMAKE_COMMAND} -E copy kramc/kram ${jni_libs_dir}/libkram.so
)

set(OPENMW_COMMON
	-DBUILD_BSATOOL=1
	-DBUILD_NIFTEST=0
	-DBUILD_ESMTOOL=0
	-DBUILD_LAUNCHER=0
	-DBUILD_MWINIIMPORTER=0
	-DBUILD_ESSIMPORTER=0
	-DBUILD_OPENCS=0
	-DBUILD_NAVMESHTOOL=1
	-DBUILD_WIZARD=0
	-DBUILD_BULLETOBJECTTOOL=0
	-DOPENMW_USE_SYSTEM_SQLITE3=OFF
	-DOPENMW_USE_SYSTEM_YAML_CPP=OFF
	-DOPENMW_USE_SYSTEM_ICU=ON
	-DOPENGL_gl_LIBRARY=${prefix}/lib/libng_gl4es.so
	-DOPENGL_glx_LIBRARY=${prefix}/lib/libng_gl4es.so
	-DOPENGL_INCLUDE_DIR=${prefix}/include/gl4es/
	-DOPENAL_INCLUDE_DIR=${prefix}/include/AL/
	-DOSG_STATIC=TRUE
	-DCMAKE_CXX_FLAGS=-std=gnu++20\ -I${prefix}/include/\ ${GLOBAL_CXXFLAGS}
	-DMyGUI_LIBRARY=${prefix}/lib/libMyGUIEngineStatic.a
)

set(OPENMW_PATCH
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/0009-windowmanagerimp-always-show-mouse-when-possible-pat.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/androidPath.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/FIXME-composie-maps-mipmaps.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/FIXME-setGlobalDefines-crash.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/force-postprocess-glsl-version.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/navmeshtool.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/ktx.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/bsatool-extract-dds-only.patch || true &&
    patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/base-changes.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/allow-more-es-versions.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/disablePBOforlocalmap.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/hackyRumble.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-log-sink.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-command-pump.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-localmap-export.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-keep-actor-names-on-hideui.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-keep-crosshair-on-hideui.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hud-visibility.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-dialogue-export.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hide-dialogue-on-hideui.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hide-container-on-hideui.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hideui-gamepad-cursor.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-barter-export.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hide-barter-on-hideui.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hide-gamewindows-on-dsmode.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-repair-export.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-restwait-export.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-travel-export.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hide-travel-on-dsmode.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-gamecursor-suppress.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-disable-hideui-action.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-hud-elements.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-b-button-choice-fix.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-spellbuyingwindow-open-signal.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-trainingwindow-open-signal.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-spellcreationdialog-open-signal.patch || true &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/companion-enchantingdialog-open-signal.patch || true
)

if(OPENMW_SRC)
	set(OPENMW_SOURCE_DIR "${OPENMW_SRC}")
	set(OPENMW_GIT_ARGS "")
else()
	set(OPENMW_SOURCE_DIR "")
	set(OPENMW_GIT_ARGS
			GIT_REPOSITORY https://github.com/OpenMW/openmw
			GIT_TAG ${OPENMW_VERSION}
			DOWNLOAD_DIR ${download_dir}
	)
endif()

ExternalProject_Add(openmw
	DEPENDS boost openal osg mygui ffmpeg sdl2 bullet lz4 luajit libicu

	${OPENMW_GIT_ARGS}
	SOURCE_DIR ${OPENMW_SOURCE_DIR}

	UPDATE_COMMAND ""
	BUILD_ALWAYS 1

	PATCH_COMMAND ${OPENMW_PATCH}
	COMMAND ${CMAKE_COMMAND} -E copy_if_different ${CMAKE_SOURCE_DIR}/openmw/patches/openmw/androidmain.cpp <SOURCE_DIR>/apps/openmw/androidmain.cpp

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	${OPENMW_COMMON}
	BUILD_COMMAND ${wrapper_command} make -j${NPROC}

	INSTALL_COMMAND ${CMAKE_COMMAND} -E make_directory ${GENERATED_ASSETS_DIR}/libopenmw/openmw
	COMMAND ${CMAKE_COMMAND} -E copy_directory resources ${GENERATED_ASSETS_DIR}/libopenmw/resources
	COMMAND ${CMAKE_COMMAND} -E copy defaults.bin gamecontrollerdb.txt ${GENERATED_ASSETS_DIR}/libopenmw/openmw/
	COMMAND ${CMAKE_COMMAND} -E copy libopenmw.so ${jni_libs_dir}/
	COMMAND ${CMAKE_COMMAND} -E copy bsatool ${jni_libs_dir}/libbsatool.so
)

add_custom_target(copy_final ALL
	DEPENDS openmw

	#Copy extras
	COMMAND ${CMAKE_COMMAND} -E copy_directory ${CMAKE_SOURCE_DIR}/openmw/extras/ ${jni_libs_dir}/

	#Final Copy of assets
	COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_ROOT}/settings.fallback.cfg ${GENERATED_ASSETS_DIR}/libopenmw/openmw/
	COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_ROOT}/openmw.cfg ${GENERATED_ASSETS_DIR}/libopenmw/openmw/
	COMMAND ${CMAKE_COMMAND} -E copy_directory ${PROJECT_ROOT}/ui ${GENERATED_ASSETS_DIR}/libopenmw/ui
	COMMAND ${CMAKE_COMMAND} -E copy ${PROJECT_ROOT}/../3rdparty-licenses.txt ${GENERATED_ASSETS_DIR}/libopenmw/
)
