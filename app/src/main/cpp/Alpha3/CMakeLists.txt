project(Alpha3)

file(GLOB INCLUDE_FILES
        *.h
)

file(GLOB SOURCE_FILES
        *.cpp
)

add_library(${PROJECT_NAME} SHARED
        ${INCLUDE_FILES}
        ${SOURCE_FILES})

target_link_libraries(${PROJECT_NAME} PRIVATE
        android
        z
        log
        Memory
        spdlog::spdlog
)

target_compile_options(${PROJECT_NAME} PRIVATE
        -w
        -O3
        -fexceptions)

add_subdirectory(deps)

set_property(TARGET ${PROJECT_NAME} PROPERTY OUTPUT_NAME Alpha3)
set_property(TARGET ${PROJECT_NAME} PROPERTY ENABLE_EXPORTS ON)
