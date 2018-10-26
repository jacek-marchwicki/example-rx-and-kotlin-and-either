# How to find StackOverflowError's

![Stack of stones](catching-stackoverflow/title.jpg)
Image from https://unsplash.com/photos/eofm5R5f9Kw

## Abstract
Sometimes in your crash reporting tool you see StackOverflowErrors breaks down the app on some device models. Some of them don’t occur on your device or the issues come about in unexpected non-reproducible circumstances. I’ll give you some tips that allow finding the errors more easily.

## Description.
Usually, StackOverflowError appears unexpectedly and sometimes it's hard to trace it just with a stack trace. On Android, different devices have different stack sizes depending on the API level or even manufacturer. So the right way to easily catch StackOverflowError is to decrease stack size in the emulator.

## Decreasing stack size
Run Dalivik emulator (before API level 15 - I haven’t checked this solution on ART emulators). Now you need to decrease the stack size:

```bash
adb root
adb shell stop
adb shell setprop dalvik.vm.extra-opts -Xss10280
adb shell start
```

Now, your emulator will restart. Check your logcat for errors like these:

```
I/vm-printf: Invalid -Xss '-Xss1', range is 1280 to 262912
I/vm-printf:   -XssN  (stack size, >= 1KB, <= 256KB)
```

If you see such a message, it means that you have set too small or too large a stack size (In this case, one byte (`-Xss1`) of stack size has obviously been too little ;) )

What’s more, if you set too small stack size, your emulator will crash because Android OS isn’t meant to support as little of it as 1kB ;) You need to experiment with values to find the one that suits your needs.

## Catching issue

Now start the app in debug mode and catch the exception before it appears.

### Setting up a breakpoint
First, set up a breakpoint for StackOverflowError in Android Studio.
1. In the menu, go to *Run* -> *View Breakpoints...* or hit *CMD+Shift+F8*
2. Click *“+”* -> *3. Jave Exception Breakpoints*
3. Search for `StackOverflowError`
4. Click *OK*
5. Click *Done*

Now, you have a breakpoint set.

### Running application

Run your application in the debug mode and use it until your exception is caught.

### Looking for important information

When the issue is caught by the breakpoint, you can easily navigate through the entire stack and find information useful for resolving the problem.

## References
* https://stackoverflow.com/questions/16843357/what-is-the-android-ui-thread-stack-size-limit-and-how-to-overcome-it
* https://stackoverflow.com/questions/7453336/setting-stack-size-to-thread-seems-to-make-no-difference-in-android
* http://blog.csdn.net/lhf_tiger/article/details/17719955