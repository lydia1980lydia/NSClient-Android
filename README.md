NS Client
===========

[![Join the chat at https://gitter.im/nightscout/NSClient-Android](https://badges.gitter.im/nightscout/NSClient-Android.svg)](https://gitter.im/nightscout/NSClient-Android?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

 - What it does?
 
 It just connects to your Nightscout server and provide support for other applications.
 
 - Does it have GUI?
 
 No GUI suitable for regular users at the moment. But maybe someone write it.
 
 - How to use it?
 
 Write your own app and listen to broadcasts to get new data from Nightscout. Send broadcasts to write to mongo database.
 
 - Any other features there?
 
 NS Client can emulate xDrip app if you don't have physical device available. It supports xDrip extension for DanaAps too (OpenAPS implementation for DanaR pump)
 
 - Some examples?
 
 Look at the and of MainActivity
 
 - And how do I get response?
 
 When you upload treatment you should receieve it back if you listen to treatment broadcasts after it goes through mongo
 
 - Do I need care about success/failure of upload?
 
 No. NS client takes care about it. Undelivered records are queued and delivered when client reconnects to server.
