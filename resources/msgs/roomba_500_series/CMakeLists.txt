cmake_minimum_required(VERSION 2.4.6)
include($ENV{ROS_ROOT}/core/rosbuild/rosbuild.cmake)

# Set the build type.  Options are:
#  Coverage       : w/ debug symbols, w/o optimization, w/ code-coverage
#  Debug          : w/ debug symbols, w/o optimization
#  Release        : w/o debug symbols, w/ optimization
#  RelWithDebInfo : w/ debug symbols, w/ optimization
#  MinSizeRel     : w/o debug symbols, w/ optimization, stripped binaries
#set(ROS_BUILD_TYPE RelWithDebInfo)

rosbuild_find_ros_package(actionlib_msgs)
include(${actionlib_msgs_PACKAGE_PATH}/cmake/actionbuild.cmake)
genaction()

rosbuild_init()

#set the default path for built executables to the "bin" directory
set(EXECUTABLE_OUTPUT_PATH ${PROJECT_SOURCE_DIR}/bin)
#set the default path for built libraries to the "lib" directory
set(LIBRARY_OUTPUT_PATH ${PROJECT_SOURCE_DIR}/lib)

#uncomment if you have defined messages
rosbuild_genmsg()
#uncomment if you have defined services
#rosbuild_gensrv()

#common commands for building c++ executables and libraries
#rosbuild_add_library(${PROJECT_NAME} src/example.cpp)
#target_link_libraries(${PROJECT_NAME} another_library)
#rosbuild_add_boost_directories()
#rosbuild_link_boost(${PROJECT_NAME} thread)
#rosbuild_add_executable(example examples/example.cpp)
#target_link_libraries(example ${PROJECT_NAME})

rosbuild_add_library(irobot_oi src/iRobot/OpenInterface.cpp)
rosbuild_add_library(go_dock src/GoDock/GoDock.cpp)

rosbuild_add_executable(roomba560_node src/roomba560.cpp src/iRobot/OpenInterface.cpp src/GoDock/GoDock.cpp)
target_link_libraries(roomba560_node cereal_port)

rosbuild_add_executable(roomba500_light_node src/roomba500_lightweight.cpp src/iRobot/OpenInterface.cpp)
target_link_libraries(roomba500_light_node cereal_port)

rosbuild_add_executable(godock_server src/godock_server.cpp src/GoDock/GoDock.cpp)
rosbuild_add_executable(godock_client src/godock_client.cpp)

