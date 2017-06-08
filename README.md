# EternalJukebox


The source files for the EternalJukebox, a rehosting of the Infinite Jukebox.  
This repo contains everything you need to host the EternalJukebox on your own server!  


You can visit the official site here:  
https://eternal.abimon.org/  
Incase you want to mess around with it without doing all the hard stuff.  


# Documentation


## Prerequisites

### Java:
##### Windows
download and install Java from https://www.java.com/en/download/  
##### Debian-based Linux distributions
For Ubuntu or Debian-based distributions execute `sudo apt-get install default-jre` in the terminal   
##### Fedora and CentOS
There is a tutorial for installing java on Fedora and CentOS at https://www.digitalocean.com/community/tutorials/how-to-install-java-on-centos-and-fedora   

### Youtube-dl:
##### Windows
Download the .exe at https://yt-dl.org/latest/youtube-dl.exe and place it in `C:\Windows\`
##### Linux
Use these commands in the terminal to install youtubedl on Linux:  
`sudo curl -L https://yt-dl.org/downloads/latest/youtube-dl -o /usr/local/bin/youtube-dl`   
`sudo chmod a+rx /usr/local/bin/youtube-dl`


## Getting the project files:
### Windows
There is two ways to get the files depending on what you prefer:
###### Without git 
You can download the zip directly here https://github.com/UnderMybrella/EternalJukebox/archive/master.zip 
Then extract them in any folder you like e.g. C:\EternalJukebox

###### Using git
First download and install Git for Windows from https://git-scm.com/downloads
After that open the Command Prompt, move to a folder you like to have the server files. 
For example you can type `CD C:\Github\` (Folder has to exist)

Then you can just use the command `git clone https://github.com/UnderMybrella/EternalJukebox.git` and the files should download to the folder you executed the command in.

### Linux
On Linux there is also two ways to get the files depending on what you prefer:
###### Without git
First off, if you have a desktop environment you can just download the zip with the files directly from https://github.com/UnderMybrella/EternalJukebox/archive/master.zip and extract them in a folder you like.  
However it might be better to use Git so you can update the files more easily!

###### Using git
You can generally install Git tools through the package-management tool that comes with your linux distribution.   

For example on **Fedora** you can use `sudo dnf install git-all`  
And on **Debian/Ubuntu** you can use `sudo apt-get install git-all`  

After that you can move to a folder you'd like to keep the files using the `cd` command and then clone EternalJukebox to it with:  
`git clone https://github.com/UnderMybrella/EternalJukebox.git`  

#### Prebuilt jar
Unless you want to build the jar yourself with Gradle you need to download it here https://eternal.abimon.org/built.jar and place it in the folder containing `default-config.json` and `jukebox_index.html`


## Configuring
First thing to do is rename default_config.json to config.json.  
then open it with notepad/notepad++ on Windows and whatever text editor you like on Linux for example nano: `nano config.json`  

Now you should go to https://developer.spotify.com/my-applications/ and log in to your spotify account.  
Then click the "Create an app" button and a new page should popup.   
There give it a name and description and click create.   
It should send you to the new app's page, the only thing you need from here is your Client ID and Client Secret  
(Note: Never share these with anyone!)  

Now fill in the Config file accordingly, you can also change the port if you'd like it to be something else but other than that there is no need to touch other options.  

#### SQL (Not required but does make some specific stuff work correctly)
For this you will need a SQL server running on the same machine that EternalJukebox will be running on.  
I won't explain how to make a SQL server but if you don't know here is a page to get you started:  
https://dev.mysql.com/doc/mysql-getting-started/en/  

If you want SQL you will need to replace the contents of the config.json with the one below and fill that in instead.  
```
{
  "comment":"This is the default config used by EternalJukebox, make sure to fill everything in for EternalJukebox to work correctly!",
  "ip":"http://$ip:$port",
  "port":11037,
  "logAllPaths":true,
  "spotifyClient":"Insert your Spotify Client ID Here",
  "spotifySecret":"Insert your Spotify Client Secret Here",
  "mysqlUsername":"Your SQL username",
  "mysqlPassword":"Your SQL password",
  "mysqlDatabase":"The SQL database used by EternalJukebox",
  "uploads":true
}  
```
When you are done save the config and move on to the next step.   

## Starting the server:

First you need to open the Terminal or Command Prompt.  
Then make sure its running in the folder that your EternalJukebox.jar is in, once again to do this use the `cd` command.  
Then execute the jar with `java -jar EternalJukebox.jar`

If everything went right it should say `Listening at http://0.0.0.0:11037`  

you should now be able to connect to it with a browser through http://localhost:11037  

Congrats you did it!  

## Building with gradle (for those who want to): 
Firstly ofcourse you need to install gradle.  
There is a full documentation on how to do this here https://gradle.org/install  

Open a Command Prompt as Administrator or Terminal on linux and move to the folder you cloned the project files to earlier. 
E.g. `cd C:\EternalJukebox`  

Now use the command `gradle clean shadowJar`
And it should start building!

Once this is finished move the .jar from build/libs/ to the project folder and rename it to EternalJukebox.jar

Now you should be done and ready to run it!
