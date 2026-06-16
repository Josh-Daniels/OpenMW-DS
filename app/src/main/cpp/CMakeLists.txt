project(Alpha3)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

cmake_minimum_required(VERSION 3.22.1)

add_subdirectory(Alpha3)
add_subdirectory(openmw)

add_dependencies(Alpha3 openmw copy_final)
