cd ./client

set CLSPATH=./config;test-service-client.jar;guava-11.0.2.jar;reflections-0.9.9-RC1.jar;xml-apis-1.0.b2.jar;jsr305-1.3.9.jar;dom4j-1.6.1.jar;javassist-3.16.1-GA.jar;activation-1.1.jar;cglib-nodep-2.2.2.jar;commons-collections-3.2.1.jar;commons-logging-1.0.4.jar;commons-pool-1.6.jar;jms-1.1.jar;jmxri-1.2.1.jar;jmxtools-1.2.1.jar;log4j-1.2.15.jar;mail-1.4.jar;remote-service-1.0.0.jar;spymemcached-2.8.0.jar
java -classpath %CLSPATH%  com.ling.test.TestMain

cd .. 