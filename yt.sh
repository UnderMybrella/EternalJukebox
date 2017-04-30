youtube-dl $1 --audio-format $3 -x -o $2 --max-filesize 10m
ffmpeg -i $2 "$2.done.$3"
rm $2
mv "$2.done.$3" $2