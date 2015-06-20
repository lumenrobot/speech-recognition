using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Newtonsoft.Json;
using System.Threading;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;
using System.IO;

namespace SpeechRecognition
{
    public class getAudioData
    {
        #region Field
        public Stream audio;
        speechRecognition speech;
        #endregion

        #region constructor
        public getAudioData()
        {

        }
        #endregion

        public void startGet()
        {
            Thread audio = new Thread(GetData);
            audio.Start();
        }

        private void GetData()
        {
            BasicDeliverEventArgs ev;
            while (true)
            {
                if (Program.connect.subscriptionWav.Next(0, out ev))
                {
                    string body = Encoding.UTF8.GetString(ev.Body);
                    JsonSerializerSettings json = new JsonSerializerSettings { TypeNameHandling = TypeNameHandling.Objects };
                    sound s = JsonConvert.DeserializeObject<sound>(body, json);
                    speech = new speechRecognition(s);
                    speech.startRecognition();
                }
            }
        }
    }
}
