# Lab 2 - Java/JVM Production Troubleshooting

This lab is designed for a **corporate laptop / WSL environment without `sudo` access**.

The goal is to practice practical Java/JVM troubleshooting for Senior DevOps/SRE interviews using a small HTTP service and safe, user-level commands.

---

# Repository Structure

```text
lab2-java-jvm-troubleshooting/
├── README.md
├── JavaTroubleshootingApp.java
└── .gitignore
```

Generated files such as `.class`, `.log`, thread dumps, GC logs, and heap dumps should not be committed.

---

# Quick Command Reference

| Category | Command | Use | Simple Example |
|---|---|---|---|
| Java | `java -version` | Check installed Java runtime version | `java -version` |
| Java | `javac -version` | Check Java compiler version | `javac -version` |
| Java | `jps -l` | List Java processes | `jps -l` |
| Process | `ps -fp <PID>` | Show details for a Java process | `ps -fp 1234` |
| CPU | `top -p <PID>` | Monitor CPU and memory for one Java process | `top -p 1234` |
| Threads | `top -H -p <PID>` | Show CPU usage by individual Java threads | `top -H -p 1234` |
| Threads | `ps -L -p <PID>` | List JVM threads and CPU usage | `ps -L -p 1234 -o pid,tid,pcpu,stat,comm --sort=-pcpu` |
| Thread Dump | `jstack <PID>` | Capture Java thread dump | `jstack 1234 > thread-dump.txt` |
| Thread Dump | `jcmd <PID> Thread.print` | Capture JVM thread information | `jcmd 1234 Thread.print` |
| Thread Dump | `kill -3 <PID>` | Ask HotSpot JVM to print a thread dump | `kill -3 1234` |
| Thread ID | `printf '%x\n' <TID>` | Convert Linux thread ID from decimal to hex | `printf '%x\n' 4388` |
| Heap | `jcmd <PID> GC.heap_info` | View JVM heap summary | `jcmd 1234 GC.heap_info` |
| Heap | `jcmd <PID> GC.class_histogram` | Show object counts and heap usage by class | `jcmd 1234 GC.class_histogram` |
| GC | `jstat -gcutil` | Monitor garbage collection usage and counts | `jstat -gcutil 1234 1000 5` |
| Memory | `free -h` | Check host-level memory usage | `free -h` |
| Process Memory | `/proc/<PID>/status` | Check Java RSS, virtual memory, and thread count | `grep -E 'VmRSS|VmSize|Threads' /proc/1234/status` |
| Port | `ss -lntp` | Find what is listening on the Java application port | `ss -lntp \| grep ':8081'` |
| HTTP | `curl` | Test application endpoint | `curl http://localhost:8081/health` |
| HTTP | `time curl` | Measure application response time | `time curl http://localhost:8081/slow` |
| Logs | `cat` | Display application log contents | `cat app.log` |
| Logs | `tail -f` | Follow application logs in real time | `tail -f app.log` |
| Process | `kill <PID>` | Gracefully terminate a Java process | `kill 1234` |

## Most Important Troubleshooting Sequence

```text
Customer Impact
      ↓
Application Health
      ↓
Host CPU / Memory
      ↓
Java Process
      ↓
JVM Threads
      ↓
Heap / GC
      ↓
Application Logs
      ↓
Dependencies
      ↓
Mitigation
      ↓
RCA
```

## High CPU Thread Mapping

```text
Java PID
   ↓
Find high CPU thread
   ↓
Linux TID
   ↓
Convert decimal → hexadecimal
   ↓
Match jstack nid=0x...
   ↓
Find exact Java stack
```

---

# 1. Check Java Environment

From the repository:

```bash
cd lab2-java-jvm-troubleshooting
```

Check Java:

```bash
java -version
javac -version
```

Check troubleshooting tools:

```bash
which jcmd
which jstack
which jstat
which jps
```

Useful tools:

```text
java
javac
jcmd
jstack
jstat
jps
```

If some JVM utilities are unavailable, thread dumps can still often be generated with:

```bash
kill -3 <PID>
```

---

# 2. Java Demo Application

The repository contains:

```text
JavaTroubleshootingApp.java
```

The application provides these endpoints:

| Endpoint | Purpose |
|---|---|
| `/health` | Basic health check |
| `/info` | Shows PID, thread count, and status |
| `/slow` | Simulates a slow request |
| `/cpu` | Simulates high CPU |
| `/memory` | Retains 10 MB of heap per request |
| `/memory-clear` | Clears retained memory references |
| `/oom` | Intentionally triggers Java heap OOM |
| `/deadlock` | Creates a Java-level deadlock |

The application listens on:

```text
127.0.0.1:8081
```

---

# 3. Compile the Application

Compile:

```bash
javac --add-modules jdk.httpserver JavaTroubleshootingApp.java
```

Verify:

```bash
ls -lh
```

You should see:

```text
JavaTroubleshootingApp.java
JavaTroubleshootingApp.class
```

---

# 4. Start the JVM

Start the Java application with a controlled heap size and GC logging:

```bash
java \
  --add-modules jdk.httpserver \
  -Xms64m \
  -Xmx128m \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  JavaTroubleshootingApp \
  > app.log 2>&1 &
```

Save the PID:

```bash
JAVA_PID=$!
echo $JAVA_PID
```

Verify:

```bash
ps -fp $JAVA_PID
```

List Java processes:

```bash
jps -l
```

---

# 5. Baseline Validation

Check port:

```bash
ss -lntp | grep ':8081'
```

Check health:

```bash
curl http://localhost:8081/health
```

Expected:

```text
UP
```

Check application information:

```bash
curl http://localhost:8081/info
```

Example:

```text
Java Troubleshooting Lab
PID: 4321
Threads: 4
Status: Running
```

Check logs:

```bash
cat app.log
```

---

# 6. High CPU Troubleshooting

Trigger CPU load:

```bash
curl http://localhost:8081/cpu
```

The request intentionally consumes CPU for about 15 seconds.

Check Java CPU usage:

```bash
top -p $JAVA_PID
```

or:

```bash
ps -fp $JAVA_PID
```

## Find the High-CPU Java Thread

Run:

```bash
ps -L -p $JAVA_PID \
  -o pid,tid,pcpu,stat,comm \
  --sort=-pcpu
```

Example:

```text
PID    TID    %CPU   STAT   COMMAND
4321   4388   95.0   Rl     java
4321   4321    1.0   Sl     java
```

Suppose:

```text
TID = 4388
```

Convert the TID to hexadecimal:

```bash
printf '%x\n' 4388
```

Example:

```text
1124
```

Now capture the thread dump:

```bash
jstack $JAVA_PID > thread-dump.txt
```

Search for:

```bash
grep -A 20 'nid=0x1124' thread-dump.txt
```

This maps:

```text
Linux high CPU
      ↓
Java PID
      ↓
Thread ID
      ↓
Hexadecimal TID
      ↓
jstack nid
      ↓
Exact Java code path
```

### Alternative

```bash
jcmd $JAVA_PID Thread.print > thread-dump.txt
```

### If `jstack` and `jcmd` Are Unavailable

```bash
kill -3 $JAVA_PID
```

Then inspect:

```bash
tail -100 app.log
```

`kill -3` does not terminate a normal HotSpot JVM. It requests a thread dump.

---

# 7. Slow Application Troubleshooting

Trigger a slow request:

```bash
time curl http://localhost:8081/slow
```

The request should take around 10 seconds.

Verify the service itself is still alive:

```bash
curl http://localhost:8081/health
```

Expected:

```text
UP
```

This demonstrates:

```text
Availability != Performance
```

## Capture a Thread Dump During the Slow Request

Terminal 1:

```bash
curl http://localhost:8081/slow
```

Terminal 2:

```bash
jstack $JAVA_PID > slow-thread-dump.txt
```

Search for waiting threads:

```bash
grep -n -A 15 'TIMED_WAITING' slow-thread-dump.txt
```

Possible real-world reasons for a slow Java request:

```text
Database query
External API
Socket read
Lock contention
Thread pool exhaustion
DNS
Network latency
GC pause
```

### Production Thought Process

```text
Health = UP
CPU = Normal
Memory = Normal
Latency = High
       ↓
Inspect application threads
       ↓
DB / External API / Locks / Network / Thread Pools
```

---

# 8. JVM Memory Growth

Check initial heap:

```bash
jcmd $JAVA_PID GC.heap_info
```

Check OS-level process memory:

```bash
grep -E 'VmRSS|VmSize|Threads' /proc/$JAVA_PID/status
```

Allocate 10 MB:

```bash
curl http://localhost:8081/memory
```

Repeat several times:

```bash
curl http://localhost:8081/memory
curl http://localhost:8081/memory
curl http://localhost:8081/memory
```

Check heap again:

```bash
jcmd $JAVA_PID GC.heap_info
```

---

# 9. Monitor Garbage Collection

Run:

```bash
jstat -gcutil $JAVA_PID 1000 10
```

Important fields:

| Field | Meaning |
|---|---|
| `E` | Eden usage |
| `O` | Old generation usage |
| `YGC` | Young GC count |
| `YGCT` | Young GC time |
| `FGC` | Full GC count |
| `FGCT` | Full GC time |
| `GCT` | Total GC time |

Check GC logs:

```bash
tail -50 gc.log
```

or:

```bash
grep -i 'pause' gc.log | tail
```

Do not assume GC is the problem just because GC is running.

Correlate:

```text
GC frequency
+
GC pause duration
+
CPU
+
Heap usage
+
Application latency
```

---

# 10. Find Objects Consuming Heap

Run:

```bash
jcmd $JAVA_PID GC.class_histogram | head -30
```

Our lab retains byte arrays.

You may see:

```text
[B
```

Meaning:

```text
byte[]
```

Clear retained objects:

```bash
curl http://localhost:8081/memory-clear
```

Check heap again:

```bash
jcmd $JAVA_PID GC.heap_info
```

---

# 11. OutOfMemoryError Demo

The application is started with:

```text
-Xmx128m
```

Trigger the controlled OOM endpoint:

```bash
curl -v http://localhost:8081/oom
```

Expected HTTP response:

```text
500
```

Check application logs:

```bash
tail -50 app.log
```

Expected Java error:

```text
java.lang.OutOfMemoryError: Java heap space
```

## Common OutOfMemoryError Types

```text
Java heap space
GC overhead limit exceeded
Metaspace
Direct buffer memory
Unable to create native thread
```

Different OOM types point to different areas.

```text
Java heap space
      ↓
Heap sizing / retained objects / memory leak


Unable to create native thread
      ↓
Thread explosion / OS limits / native memory


Metaspace
      ↓
Class loading / classloader leak
```

### Important Interview Point

Increasing:

```text
-Xmx
```

is not automatically the correct solution.

First identify whether the problem is:

```text
Memory leak
Unexpected traffic
Large object allocation
Unbounded cache
Poor heap sizing
GC pressure
Thread explosion
Native memory
```

---

# 12. Java Deadlock Demo

Trigger:

```bash
curl http://localhost:8081/deadlock
```

Expected:

```text
Deadlock scenario started
```

The application health endpoint may still work:

```bash
curl http://localhost:8081/health
```

Capture thread dump:

```bash
jstack $JAVA_PID > deadlock.txt
```

Search:

```bash
grep -i -A 30 deadlock deadlock.txt
```

HotSpot may report:

```text
Found one Java-level deadlock
```

The demo creates:

```text
deadlock-thread-1
deadlock-thread-2
```

Concept:

```text
Thread 1
   |
holds Lock A
   |
waits for Lock B
          ↑
          |
       held by
          |
Thread 2
   |
holds Lock B
   |
waits for Lock A
```
Restart the application after the deadlock demo if required:

```bash
kill $JAVA_PID
```

Start again:

```bash
java \
  --add-modules jdk.httpserver \
  -Xms64m \
  -Xmx128m \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  JavaTroubleshootingApp \
  > app.log 2>&1 &

JAVA_PID=$!
```

Verify:

```bash
curl http://localhost:8081/health
```

---

# 13. GC Pressure Demo

Generate retained memory:

```bash
for i in 1 2 3 4 5
do
    curl -s http://localhost:8081/memory
    echo
done
```

Monitor GC:

```bash
jstat -gcutil $JAVA_PID 1000 10
```

Check GC log:

```bash
tail -50 gc.log
```

Production pattern to watch:

```text
High latency
+
High CPU
+
Frequent Full GC
+
Heap nearly full
       ↓
Possible GC pressure
```

---

# 14. JVM Heap vs Process Memory

Check JVM heap:

```bash
jcmd $JAVA_PID GC.heap_info
```

Check OS process memory:

```bash
grep -E 'VmRSS|VmSize' /proc/$JAVA_PID/status
```

These values do not need to match.

Java process memory includes more than heap:

```text
Java Process Memory
       |
       +--- Java Heap
       |
       +--- Metaspace
       |
       +--- Thread Stacks
       |
       +--- Code Cache
       |
       +--- Direct Buffers
       |
       +--- Native Libraries
       |
       +--- JVM Native Memory
```

Therefore:

```text
-Xmx != Total Java process memory
```

This is an important Senior DevOps/SRE interview point.

---

# 15. Port Conflict Demo

Stop Java:

```bash
kill $JAVA_PID
```

Start a dummy service on port `8081`:

```bash
python3 -m http.server 8081 > dummy.log 2>&1 &
```

Save PID:

```bash
DUMMY_PID=$!
echo $DUMMY_PID
```

Try to start Java again.

Check:

```bash
cat app.log
```

You should see an error related to:

```text
Address already in use
```

Find the listener:

```bash
ss -lntp | grep ':8081'
```

Inspect:

```bash
ps -fp $DUMMY_PID
```

Stop the dummy process:

```bash
kill $DUMMY_PID
```

Verify the port is free:

```bash
ss -lnt | grep ':8081'
```

No output means nothing is listening on the port.

Restart Java.

---

# 16. Production Scenario - High CPU + 5xx Errors

Interview scenario:

> A Java production service suddenly starts consuming 100% CPU and users report 5xx errors. What do you do?

Use this sequence:

```text
1. Confirm customer impact
        ↓
2. Check recent deployments/config changes
        ↓
3. Check host CPU and memory
        ↓
4. Identify Java PID
        ↓
5. Identify high-CPU Java thread
        ↓
6. Capture thread dumps
        ↓
7. Check heap and GC
        ↓
8. Check application logs
        ↓
9. Check DB/external dependencies
        ↓
10. Mitigate
        ↓
11. Validate recovery
        ↓
12. RCA + prevention
```

---

# 17. Important JVM Concepts

## `-Xms`

Initial JVM heap size.

Example:

```text
-Xms64m
```

## `-Xmx`

Maximum JVM heap size.

Example:

```text
-Xmx128m
```

## Thread States

Common thread states:

| State | Meaning |
|---|---|
| `RUNNABLE` | Running or ready to run |
| `WAITING` | Waiting indefinitely |
| `TIMED_WAITING` | Waiting for a specified period |
| `BLOCKED` | Waiting to acquire a monitor/lock |
| `TERMINATED` | Thread has completed |

## Thread Dump

A thread dump shows the current state and stack trace of JVM threads.

Useful for:

```text
High CPU
Deadlocks
Lock contention
Slow requests
Thread pool exhaustion
Waiting on dependencies
```

---

# 18. Git Ignore

Create:

```bash
vi .gitignore
```

Add:

```gitignore
*.class
*.log
*.hprof
thread-dump*.txt
slow-thread-dump*.txt
deadlock.txt
```

This keeps generated troubleshooting files out of Git.

---

# 19. Lab Cleanup

List Java processes:

```bash
jps -l
```

Stop the Java application:

```bash
kill $JAVA_PID 2>/dev/null
```

Stop the dummy process if it is still running:

```bash
kill $DUMMY_PID 2>/dev/null
```

Check port:

```bash
ss -lnt | grep ':8081'
```

Check remaining processes:

```bash
ps -ef | grep JavaTroubleshootingApp
```

Check generated files:

```bash
ls -lh
```
---

# Key Lab 2 Technique to Remember

```text
top / ps
   ↓
Java PID
   ↓
top -H / ps -L
   ↓
High CPU TID
   ↓
printf '%x\n' TID
   ↓
jstack
   ↓
nid=0x...
   ↓
Exact Java stack
   ↓
Root Cause
```

