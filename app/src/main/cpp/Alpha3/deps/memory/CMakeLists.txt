project(Memory)

file(GLOB INCLUDE_FILES
        include/*.h)

file(GLOB SOURCE_FILES
        include/*.cpp)

add_library(${PROJECT_NAME} INTERFACE)

target_sources(${PROJECT_NAME} INTERFACE
    ${INCLUDE_FILES}
    ${SOURCE_FILES})

set(glosshook_path "${CMAKE_CURRENT_SOURCE_DIR}/lib/${ANDROID_ABI}/libGlossHook.a")
add_library(GlossHook STATIC IMPORTED)
set_target_properties(GlossHook PROPERTIES IMPORTED_LOCATION ${glosshook_path})

target_link_libraries(${PROJECT_NAME} INTERFACE
    GlossHook
)

target_include_directories(${PROJECT_NAME} INTERFACE
    "${CMAKE_CURRENT_SOURCE_DIR}/include")
