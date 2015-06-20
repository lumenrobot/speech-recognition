using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;

namespace SpeechRecognition
{
    class Program
    {
        static public connection connect;
        static public getAudioData gad;

        static void Main(string[] args)
        {
            connect = new connection();
            connect.connectTo();

            gad = new getAudioData();
            gad.startGet();
        }
    }
}
