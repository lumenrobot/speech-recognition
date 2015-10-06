# Speech Recognition

Recognize sentence from voice using google speech API.

## Dependencies

1. [flac for Windows](https://xiph.org/flac/download.html).

## Running

When dialog show when running flac.exe, uncheck "Always ask" then click Allow.

## Tutorial

1. Di Visual Studio, open solution dari folder git: `speech-recognition`
2. Buka file berikut: `speechRecognition.cs`
3. Lalu uncomment & edit baris berikut:

		WebProxy ITBproxy = new WebProxy();
		ITBproxy.Address = new Uri("http://cache.itb.ac.id:8080", true);
		ITBproxy.Credentials = new NetworkCredential("username_AI3_Anda", "password_AI3_Anda");

4. Run
5. Pastikan program jalan tanpa pesan error
6. Biarkan dan jangan diclose
	* Program ini akan menunggu data audio yang dikirim via RabbitMQ dari program lain, untuk derecognize menjadi text
