youtube-dl $1 --audio-format 'mp3' -x -o $2 --max-filesize 10m
ffmpeg -i $2 "$2.done.mp3"
rm $2
mv "$2.done.mp3" $2