# Jefferson

Parses PDF documents of parliamentary procedure following the Jefferson Manual.

This is made for the Missouri House of Representatives.

## Usage

This currently requires a lot of hand holding, you would need to be rather comfortable with a computer to run this on your own.

The short version of it is that there's a script, `journals/fetch`, which pulls down PDF journals.
Give it a house session and a range of journal indexes, like `fetch 191 1 80` and it'll pull down files for you.

Then pre-process the files into txt documents using `jefferson.Sanitizer`, and then process those text files using `jefferson.Main`.
You'll need to modify `jefferson.Main` to do what you like with the end result.

## License

GPLv3
