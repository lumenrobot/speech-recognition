using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Newtonsoft.Json;

namespace SpeechRecognition
{
    public class recognizer
    {
        [JsonProperty("name")]
        public string name { get; set; }
        [JsonProperty("result")]
        public string result { get; set; }
        [JsonProperty("date")]
        public string date { get; set; }
    }

    public class sound
    {
        [JsonProperty("name")]
        public string name { get; set; }
        [JsonProperty("content")]
        public string content { get; set; }
        [JsonProperty("language")]
        public string language { get; set; }
    }
}
