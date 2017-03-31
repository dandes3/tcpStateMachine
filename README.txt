-------------------------------------------
| Quint Guvernator, Don Andes
| Project 2: TCP State Machine
-------------------------------------------

Created and submitted as coursework for CSCI 434, The College of William & Mary, Spring 2017.

---------------------------------------------------------------------------------------------
----- This code is provided as is and has been created strictly for educational purposes ----
---------------------------------------------------------------------------------------------

---------------------------------------------------------------------------------------------
|                   _______            __                           __                      |
|                   \      \    ____ _/  |_ __  _  __ ____ _______ |  | __                  |
|                   /   |   \ _/ __ \\   __\\ \/ \/ //  _ \\_  __ \|  |/ /                  |
|                  /    |    \\  ___/ |  |   \     /(  <_> )|  | \/|    <                   |
|                  \____|__  / \___  >|__|    \/\_/  \____/ |__|   |__|_ \                  |
|                          \/      \/                                   \/                  |
|                                                                                           |
---------------------------------------------------------------------------------------------

Notes--

      As per our original hopes, we inititally attempted to integrate and write this project 
       in Scala. While Scala does have the ability to integrate into existing Java code, it 
       proved beyond the scope of our ability for this project. We have instead opted to 
       fall back on the original specifications and deliver our final product in Java. 

       We *will* try again with project three. 

      We want to again express our gratitude for allowing us to attempt this assignment in 
       an alternate JVM language, and by doing so, push the boundaries of our knowledge.  


Usage--

      We have included a Java makefile for ease of use. Invoking "$ make" at the command line
       will compile the StudentSocketImpl-skel.java, client2.java, and server2.java files- 
       along with our additional test files we have created. 

      As per the specifications, the two sides of the program should be invoked with "$ java 
       -DUDPPORT=8816 -DLOSSRATE=0.50 server2 8817" and "$ java -DUDPPORT=8816 -DLOSSRATE=0.50 
       client2 servermachinename 8817" respectively after compilation. We have tested our 
       program and expect it to work with a DLOSSRATE up to 0.60. 

