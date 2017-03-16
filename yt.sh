youtube-dl $1 --audio-format 'mp3' -x -o $2
ffmpeg -i $2 "$2.done.mp3"
rm $2
mv "$2.done.mp3" $2