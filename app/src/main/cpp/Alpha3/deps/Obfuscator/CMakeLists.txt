project(obfuscator)

file(GLOB INCLUDE_FILES
        obfuscator.h
)


add_library(${PROJECT_NAME} INTERFACE)

target_sources(${PROJECT_NAME} INTERFACE
        ${INCLUDE_FILES}
)

target_include_directories(${PROJECT_NAME} INTERFACE
        "${CMAKE_CURRENT_SOURCE_DIR}"
)
