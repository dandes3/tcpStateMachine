JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	 $(JC) $(JFLAGS) $*.java

CLASSES = \
        StudentSocketImpl-skel.java \
        client2.java \
        client0.java\
        server2.java \
        server0.java 

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
