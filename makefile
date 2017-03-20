JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	 $(JC) $(JFLAGS) $*.java

CLASSES = \
        StudentSocketImpl-skel.java \
        client2.java \
        server2.java 

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
