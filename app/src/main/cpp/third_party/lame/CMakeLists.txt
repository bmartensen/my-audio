# LAME 3.100 MP3 encoder (LGPL-2.0-or-later), see README.md for provenance. Built as its own shared
# library (libmp3lame.so, mp3lame.dll) that the engine links dynamically, as the LGPL expects; shared by
# Android and desktop. Only the encoder is built: no decoder (mpglib), frontend or assembly.
file(GLOB LAME_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/libmp3lame/*.c)
add_library(mp3lame SHARED ${LAME_SOURCES})
target_include_directories(mp3lame PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_include_directories(mp3lame PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/config ${CMAKE_CURRENT_SOURCE_DIR}/libmp3lame)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
if(MSVC)
    # LAME ships no export list that matches its 3.100 sources; export the whole C API.
    set_target_properties(mp3lame PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
    target_compile_options(mp3lame PRIVATE /W0 /O2)
    target_compile_definitions(mp3lame PRIVATE _CRT_SECURE_NO_WARNINGS _USE_MATH_DEFINES)
else()
    target_compile_options(mp3lame PRIVATE -w -O2)
    target_link_libraries(mp3lame PRIVATE m)
endif()
