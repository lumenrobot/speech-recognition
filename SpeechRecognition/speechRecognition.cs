using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using RabbitMQ.Client;
using RabbitMQ.Client.MessagePatterns;
using RabbitMQ.Client.Events;
using Newtonsoft.Json;
using System.IO;
using System.Threading;
using System.Net;
using System.Diagnostics;

namespace SpeechRecognition
{
    public class speechRecognition
    {
        static sendTextData sendText = new sendTextData();
        string language;
        public speechRecognition(sound input)
        {
            byte[] buffer = Convert.FromBase64String(input.content);
            string fileIn = Environment.CurrentDirectory + "/recording.wav";
            File.WriteAllBytes(fileIn, buffer);
            this.language = input.language;
        }
        public void startRecognition()
        {
            Thread a = new Thread(recognize);
            a.Start();
        }
        public void recognize()
        {
            //convertToFlac();
            googleR();
        }
        private void convertToFlac()
        {
            try
            {
                string fileIn = Environment.CurrentDirectory + "/recording.wav";
                FileInfo file = new FileInfo(fileIn);
                FileStream stream = null;
                try
                {
                    stream = file.Open(FileMode.Open, FileAccess.ReadWrite, FileShare.None);
                }
                catch (IOException)
                {
                    //the file is unavailable because it is:
                    //still being written to
                    //or being processed by another thread
                    //or does not exist (has already been processed)
                }
                finally
                {
                    if (stream != null)
                        stream.Close();
                    ProcessStartInfo start = new ProcessStartInfo();
                    //start.FileName = @"""C:\Program Files\flac-1.3.1-win\flac-1.3.1-win\win64\flac.exe""";
                    start.FileName = @"flac.exe";
                    start.WorkingDirectory = Environment.CurrentDirectory;
                    start.Arguments = "-f recording.wav";
                    start.UseShellExecute = true;
                    start.CreateNoWindow = true;
                    Process process = Process.Start(start);
                    process.WaitForExit();
                }
            }
            catch (Exception e)
            {
                Console.WriteLine("error : " + e.ToString());
            }
        }
        private void googleR()
        {
            Console.WriteLine("starting recognition...");
            string fileIn = Environment.CurrentDirectory + "/recording.flac";
            string ACCESS_GOOGLE_SPEECH_KEY = "AIzaSyDC8nM1S0cLpXvRc8TXrDoey-tqQsoBGnM";
            bool flag = false;
            FileStream filestream;
            convertToFlac();
            while (!flag)
            {
                flag = File.Exists(Environment.CurrentDirectory + "/recording.flac");
                if (flag)
                {
                    filestream = File.OpenRead(fileIn);
                    MemoryStream memoryStream = new MemoryStream();
                    memoryStream.SetLength(filestream.Length);
                    filestream.Read(memoryStream.GetBuffer(), 0, (int)filestream.Length);
                    byte[] BA_AudioFile = memoryStream.GetBuffer();
                    HttpWebRequest _HWR_SpeechToText = null;
                    WebProxy ITBproxy = new WebProxy();
                    ITBproxy.Address = new Uri("http://cache.itb.ac.id:8080", true);
                    //ITBproxy.Credentials = new NetworkCredential("ahmadsyarif", "920420");
                    ITBproxy.Credentials = new NetworkCredential("hendyirawan", "");
                    Console.WriteLine("language : " + language);
                    _HWR_SpeechToText = (HttpWebRequest)HttpWebRequest.Create("https://www.google.com/speech-api/v2/recognize?output=json&lang=" + language + "&key=" + ACCESS_GOOGLE_SPEECH_KEY);
                    _HWR_SpeechToText.Proxy = ITBproxy;
                    _HWR_SpeechToText.Credentials = CredentialCache.DefaultCredentials;
                    _HWR_SpeechToText.Method = "POST";
                    _HWR_SpeechToText.ContentType = "audio/x-flac; rate=16000";
                    _HWR_SpeechToText.ContentLength = BA_AudioFile.Length;
                    Stream stream = _HWR_SpeechToText.GetRequestStream();
                    stream.Write(BA_AudioFile, 0, BA_AudioFile.Length);
                    stream.Close();
                    HttpWebResponse HWR_Response = (HttpWebResponse)_HWR_SpeechToText.GetResponse();
                    StreamReader SR_Response = new StreamReader(HWR_Response.GetResponseStream());
                    string responseFromServer = (SR_Response.ReadToEnd());

                    String[] jsons = responseFromServer.Split('\n');
                    String text = "";
                    foreach (String j in jsons)
                    {
                        dynamic jsonObject = JsonConvert.DeserializeObject(j);
                        if (jsonObject == null || jsonObject.result.Count <= 0)
                        {
                            continue;
                        }
                        text = jsonObject.result[0].alternative[0].transcript;
                    }
                    sendText.resultHandling(text);
                    Console.Write("recognized words : ");
                    Console.ForegroundColor = ConsoleColor.Blue;
                    Console.Write(text);
                    Console.ResetColor();
                    Console.WriteLine("");
                    Console.WriteLine("");
                    filestream.Close();
                    File.Delete(fileIn);
                    fileIn = Environment.CurrentDirectory + "/recording.wav";
                    File.Delete(fileIn);
                }
            }
        }
    }
}