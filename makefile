JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	 $(JC) $(JFLAGS) $*.java

CLASSES = \
        StudentSocketImpl-skel.java \
	client3.java \
        client2.java \
	client1.java \
        client0.java \
	server3.java \
        server2.java \
	server1.java \
        server0.java 

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
