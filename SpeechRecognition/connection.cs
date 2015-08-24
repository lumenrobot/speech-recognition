using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using RabbitMQ.Client;
using RabbitMQ.Client.MessagePatterns;
using RabbitMQ.Client.Events;

namespace SpeechRecognition
{
    public class connection
    {
        #region Field
        public IModel channelSend, channelGet;
        public string channelKeySend, channelKeyGet;
        public Subscription subscriptionWav;
        #endregion

        public connection()
        {
            channelKeyGet = "lumen.audio.wav.stream"; // get wave data from RabbitMQ, used for speech recognition
            channelKeySend = "lumen.audio.speech.recognition"; // send text data to RabbitMQ, used for speech recognition
        }

        public void connectTo()
        {
            Console.WriteLine("Start...");
            Console.WriteLine("");
            ConnectionFactory factory = new ConnectionFactory(); // establish connection
            // factory.Uri = "amqp://lumen:lumen@169.254.18.223/%2F"; // ip syarif
            factory.Uri = "amqp://localhost/%2F"; // local
            try
            {
                IConnection connection = factory.CreateConnection();

                channelGet = connection.CreateModel();
                QueueDeclareOk queue = channelGet.QueueDeclare("", true, false, true, null);
                channelGet.QueueBind(queue.QueueName, "amq.topic", channelKeyGet);
                subscriptionWav = new Subscription(channelGet, queue.QueueName);

                channelSend = connection.CreateModel();
                connection.AutoClose = true;
                            
                Console.WriteLine("Connected to Server.");
                Console.WriteLine("");
                Console.WriteLine("=================================================");
                Console.WriteLine("");
            }

            catch (Exception e)
            {
                Console.WriteLine(e.ToString());
            }
        }
    }
}
