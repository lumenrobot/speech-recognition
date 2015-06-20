using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using RabbitMQ.Client;
using RabbitMQ.Client.MessagePatterns;
using RabbitMQ.Client.Events;
using Newtonsoft.Json;

namespace SpeechRecognition
{
    public class sendTextData
    {
        // Global variable
        IModel channelSend;
        string channelKeySend;

        public sendTextData()
        {
            channelSend = Program.connect.channelSend;
            channelKeySend = Program.connect.channelKeySend;
        }

        // Handle data result and send it to RabbitMQ.
        public void resultHandling(string result1)
        {
            recognizer hasil = new recognizer { name = "aaa", result = result1, date = DateTime.Now.ToString() };
            string body = JsonConvert.SerializeObject(hasil); //serialisasi data menjadi string
            byte[] a = Encoding.UTF8.GetBytes(body);
            channelSend.BasicPublish("amq.topic", channelKeySend, null, a); //mengirim data ke RabbitMQ
        }
    }
}
