package gecko10000.telefuse

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        throw IllegalArgumentException("Please specify a directory.")
    }
    App(args[0])
}
