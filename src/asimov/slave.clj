(ns asimov.slave
  (require
   [org.httpkit.server :as server]
   [compojure
    [core :as compojure :refer [defroutes GET POST]]
    [handler :refer [api]]]
   [necessary-evil.core :as xml-rpc]))


(def ros-url "http://192.168.56.101:11311/")


bool (1)
unsigned 8-bit int
uint8_t (2)
bool
int8
signed 8-bit int
int8_t
int
uint8
unsigned 8-bit int
uint8_t
int (3)
int16
signed 16-bit int
int16_t
int
uint16
unsigned 16-bit int
uint16_t
int
int32
signed 32-bit int
int32_t
int
uint32
unsigned 32-bit int
uint32_t
int
int64
signed 64-bit int
int64_t
long
uint64
unsigned 64-bit int
uint64_t
long
float32
32-bit IEEE float
float
float
float64
64-bit IEEE float
double
float
string
ascii string (4)
std::string
string