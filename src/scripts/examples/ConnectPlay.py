import string, cgi, time
import sys
from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer

filename =""

class MyHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        self.do_POST()

    def do_POST(self):
        if self.path == "/Status":
            print("PATH = STATUS")
            self.printParams()
            self.postOK()
        else if self.path == "/Connected":
            self.printParams()
            self.handleIncomingCall()   

    def printParams(self):
        params = self.parseParams()
        for field in params.keys():
            print( field + "=" + "".join(params[field]))
    
    def postOK(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html")
        self.end_headers()
        
    def handleConnectedCall(self):
        self.postOK()
        str = """
                <Response>
                    <Play>
                        <Action>http://localhost:8100/Status</Action>
                        <MediaUrl>""" + filename + """</MediaUrl>
                    </Play>>
                </Response>
            """
        self.wfile.write(str)
        return

    def parseParams(self):
        length = int(self.headers.getheader('Content-Length'))
        params = cgi.parse_qs(self.rfile.read(length), keep_blank_values=1)
        return params

    def postCall():
        data = urllib.urlencode( {"To" : number1, 
                              "From": number2, 
                              "Url" : "http://" + (listeningIp + ":" + str(listeningPort) + "/Connected")} )
        f = urllib.urlopen( "http://" + bluescaleIp + ":" + str(bluescalePort) + "/Calls/" ,data)
        r = f.read()
        print(r)


def main():
    try:
        server = HTTPServer( ('', 8081), MyHandler)
        print("started, will play " + filename)
        server.serve_forever()
        print("serving...")
        #time.sleep(5000)
    except Exception, err:
        print("damn error = " + str(err))

if __name__ == '__main__':
    filename = sys.argv[1]
    main()
