/* 2012-05-20 Version 2.0

Thanks John Reagan for updates to original code by Clark Elliott.

Modified further on 2020-05-19

-----------------------------------------------------------------------

Play with this code. Add your own comments to it before you turn it in.

-----------------------------------------------------------------------

NOTE: This is NOT a suggested implementation for your agent platform,
but rather a running example of something that might serve some of
your needs, or provide a way to start thinking about what YOU would like to do.
You may freely use this code as long as you improve it and write your own comments.

-----------------------------------------------------------------------

TO EXECUTE: 

1. Start the HostServer in some shell. >> java HostServer

1. start a web browser and point it to http://localhost:4242. Enter some text and press
the submit button to simulate a state-maintained conversation.

2. start a second web browser, also pointed to http://localhost:4242 and do the same. Note
that the two agents do not interfere with one another.

3. To suggest to an agent that it migrate, enter the string "migrate"
in the text box and submit. The agent will migrate to a new port, but keep its old state.

During migration, stop at each step and view the source of the web page to see how the
server informs the client where it will be going in this stateless environment.

-----------------------------------------------------------------------------------

COMMENTS:

This is a simple framework for hosting agents that can migrate from
one server and port, to another server and port. For the example, the
server is always localhost, but the code would work the same on
different, and multiple, hosts.

State is implemented simply as an integer that is incremented. This represents the state
of some arbitrary conversation.

The example uses a standard, default, HostListener port of 4242.

-----------------------------------------------------------------------------------

DESIGN OVERVIEW

Here is the high-level design, more or less:

HOST SERVER
  Runs on some machine
  Port counter is just a global integer incrememented after each assignment
  Loop:
    Accept connection with a request for hosting
    Spawn an Agent Looper/Listener with the new, unique, port

AGENT LOOPER/LISTENER
  Make an initial state, or accept an existing state if this is a migration
  Get an available port from this host server
  Set the port number back to the client which now knows IP address and port of its
         new home.
  Loop:
    Accept connections from web client(s)
    Spawn an agent worker, and pass it the state and the parent socket blocked in this loop
  
AGENT WORKER
  If normal interaction, just update the state, and pretend to play the animal game
  (Migration should be decided autonomously by the agent, but we instigate it here with client)
  If Migration:
    Select a new host
    Send server a request for hosting, along with its state
    Get back a new port where it is now already living in its next incarnation
    Send HTML FORM to web client pointing to the new host/port.
    Wake up and kill the Parent AgentLooper/Listener by closing the socket
    Die

WEB CLIENT
  Just a standard web browser pointing to http://localhost:4242 to start.

  -------------------------------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

/*
    This listens on port 4242 and spawns AgentListener processes that listen on NextPort
    that the HostServer keeps track of
*/
public class HostServer {
    // static int that tracks port usages. Starts at 3000 but adds 1
    // so first port used is 3001
    public static int NextPort = 3000;

    public static void main(String[] a) throws IOException {
        int q_len = 6;
        // port the Host server listens on
        int port = 4242;
        Socket sock;

        ServerSocket servsock = new ServerSocket(port, q_len);
        System.out.println("B. Reinhard DIA Master receiver started at port 4242.");
        System.out.println("Connect from 1 to 3 browsers using \"http:\\\\localhost:4242\"\n");
        // start listening loop. Will handle new or migrate requests
        while (true) {
            // increment Nexport for the agent listener
            NextPort = NextPort + 1;
            sock = servsock.accept();
            System.out.println("Starting AgentListener at port " + NextPort);
            // spawn new agenstListener
            new AgentListener(sock, NextPort).start();
        }

    }
}

/*
 * AgentWorker is spawned from AgentListener. Handles migrating the parent
 * AgentListener if part of request
 */
class AgentWorker extends Thread {

    Socket sock; // socket for connecting to client
    agentHolder parentAgentHolder; // reference to the spawning AgentListener - agenstState and socket
    int localPort;

    AgentWorker(Socket s, int prt, agentHolder ah) {
        sock = s;
        localPort = prt;
        parentAgentHolder = ah;
    }

    public void run() {
        PrintStream out = null;
        BufferedReader in = null;
        String NewHost = "localhost";
        // port of the main host listener - should be a parameter probably
        int NewHostMainPort = 4242;
        String buf = "";
        int newPort;
        Socket clientSock;
        BufferedReader fromHostServer;
        PrintStream toHostServer;

        try {
            // output and input from socket
            out = new PrintStream(sock.getOutputStream());
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            // get input from client
            String inLine = in.readLine();
            // html string for response
            StringBuilder htmlString = new StringBuilder();

            System.out.println();
            System.out.println("Request line: " + inLine);

            // if migrate is in the request
            if (inLine.indexOf("migrate") > -1) {

                // reach out to HostServer to get the next available port
                clientSock = new Socket(NewHost, NewHostMainPort);
                fromHostServer = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
                toHostServer = new PrintStream(clientSock.getOutputStream());
                toHostServer.println("Please host me. Send my port! [State=" + parentAgentHolder.agentState + "]");
                toHostServer.flush();

                for (;;) {
                    // wait for host server to respond with port
                    buf = fromHostServer.readLine();
                    if (buf.indexOf("[Port=") > -1) {
                        break;
                    }
                }

                // get the port
                String tempbuf = buf.substring(buf.indexOf("[Port=") + 6, buf.indexOf("]", buf.indexOf("[Port=")));
                newPort = Integer.parseInt(tempbuf);
                System.out.println("newPort is: " + newPort);

                // prepare the html to send to client
                htmlString.append(AgentListener.sendHTMLheader(newPort, NewHost, inLine));
                // add migration html info
                htmlString.append("<h3>We are migrating to host " + newPort + "</h3> \n");
                htmlString.append(
                        "<h3>View the source of this page to see how the client is informed of the new location.</h3> \n");
                htmlString.append(AgentListener.sendHTMLsubmit());

                System.out.println("Killing parent listening loop.");
                // kill the AgentListener that was just migrated by closing socket
                ServerSocket ss = parentAgentHolder.sock;
                ss.close();

            } else if (inLine.indexOf("person") > -1) {
                // recieved input, increment the agent state from the AgenstListener reference
                // stored
                // in parentAgentHolder
                parentAgentHolder.agentState++;

                // update html
                htmlString.append(AgentListener.sendHTMLheader(localPort, NewHost, inLine));
                htmlString.append(
                        "<h3>We are having a conversation with state   " + parentAgentHolder.agentState + "</h3>\n");
                htmlString.append(AgentListener.sendHTMLsubmit());

            } else {
                // handle invalid requests - not a person or migrate query
                // favicon.ico?
                htmlString.append(AgentListener.sendHTMLheader(localPort, NewHost, inLine));
                htmlString.append("You have not entered a valid request!\n");
                htmlString.append(AgentListener.sendHTMLsubmit());

            }
            // send the html
            AgentListener.sendHTMLtoStream(htmlString.toString(), out);

            // responded, close socket
            sock.close();

        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

}

/**
 * Class to hold information about an AgentListener
 */
class agentHolder {
    // socket
    ServerSocket sock;
    // the agenst state
    int agentState;

    agentHolder(ServerSocket s) {
        sock = s;
    }
}

/*
 * AgenstListener is spawned by HostServer and listens on the port parameter it
 * is passed in
 */
class AgentListener extends Thread {
    // variables for local instance, initialized in constructor
    Socket sock;
    int localPort;

    AgentListener(Socket As, int prt) {
        sock = As;
        localPort = prt;
    }

    // agentState that keeps track of request count
    // can be overriden if State part of request
    int agentState = 0;

    // main entry point
    public void run() {
        BufferedReader in = null;
        PrintStream out = null;
        String NewHost = "localhost";
        System.out.println("In AgentListener Thread");
        try {
            String buf;
            out = new PrintStream(sock.getOutputStream());
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            // read input from socket
            buf = in.readLine();

            // if have some initial state, set agentState to that
            if (buf != null && buf.indexOf("[State=") > -1) {
                // have prexisting state, read it in and set agenstState to it
                String tempbuf = buf.substring(buf.indexOf("[State=") + 7, buf.indexOf("]", buf.indexOf("[State=")));
                agentState = Integer.parseInt(tempbuf);
                System.out.println("agentState is: " + agentState);

            }

            System.out.println(buf);
            // html response
            StringBuilder htmlResponse = new StringBuilder();

            // build the htlm response out. Include a new form
            htmlResponse.append(sendHTMLheader(localPort, NewHost, buf));
            htmlResponse.append("Now in Agent Looper starting Agent Listening Loop\n<br />\n");
            htmlResponse.append("[Port=" + localPort + "]<br/>\n");
            htmlResponse.append(sendHTMLsubmit());
            // output the html
            sendHTMLtoStream(htmlResponse.toString(), out);

            // open door bell socket to listen
            ServerSocket servsock = new ServerSocket(localPort, 2);
            // store the instance agent state in the agentHolder class
            agentHolder agenthold = new agentHolder(servsock);
            agenthold.agentState = agentState;

            // start listener
            while (true) {
                sock = servsock.accept();
                System.out.println("Got a connection to agent at port " + localPort);
                // Start agentWorker thread that has a reference to this local instance of
                // AgentListener
                new AgentWorker(sock, localPort, agenthold).start();
            }

        } catch (IOException ioe) {
            // connection failed or process was migrated
            System.out.println("Either connection failed, or just killed listener loop for agent at port " + localPort);
            System.out.println(ioe);
        }
    }

    // add the form html
    // include the new port so the client can communicate
    static String sendHTMLheader(int localPort, String NewHost, String inLine) {

        StringBuilder htmlString = new StringBuilder();

        htmlString.append("<html><head> </head><body>\n");
        htmlString.append("<h2>This is for submission to PORT " + localPort + " on " + NewHost + "</h2>\n");
        htmlString.append("<h3>You sent: " + inLine + "</h3>");
        htmlString.append("\n<form method=\"GET\" action=\"http://" + NewHost + ":" + localPort + "\">\n");
        htmlString.append("Enter text or <i>migrate</i>:");
        htmlString.append("\n<input type=\"text\" name=\"person\" size=\"20\" value=\"YourTextInput\" /> <p>\n");

        return htmlString.toString();
    }

    // add the submit element to the form
    static String sendHTMLsubmit() {
        return "<input type=\"submit\" value=\"Submit\"" + "</p>\n</form></body></html>\n";
    }

    // add response headers and send output
    static void sendHTMLtoStream(String html, PrintStream out) {

        out.println("HTTP/1.1 200 OK");
        out.println("Content-Length: " + html.length());
        out.println("Content-Type: text/html");
        out.println("");
        out.println(html);
    }

}
