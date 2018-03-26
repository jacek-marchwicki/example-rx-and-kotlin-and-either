# How to find StackOverflowError's

![Stack of stones](catching-stackoverflow/title.jpg)
Image from https://unsplash.com/photos/eofm5R5f9Kw

## Abstract
Sometimes you see StackOverflowError's on some devices. Some of them you can't reproduce on your device or the crashes come about in unexpected situations. There are some tips that will allow finding the issues more easily.

## Description.
Usually, StackOverflowError appears unexpectedly and sometimes it's hard to trace them with just a stack trace that you will get from a crash on your device or from a crash on your crash reporter. On Android, different devices have different stack size depending on the API level or even manufacturer. So the good way is to easily catch StackOverflowError is to decrease a stack trace on an emulator.

## Decreasing stack size
Run Dalivik emulator (before API level 5, I didn’t check this solution on ART emulators). Now you need to decrease the stack size:

```bash
adb root
adb shell stop
adb shell setprop dalvik.vm.extra-opts -Xss10280
adb shell start
```

Now, your emulator will restart. You need to look at your logcat for errors like these:

```
I/vm-printf: Invalid -Xss '-Xss1', range is 1280 to 262912
I/vm-printf:   -XssN  (stack size, >= 1KB, <= 256KB)
```

If you see such a notification, it means that you have given too small or too large a stack size (In this case `-Xss1` - One byte of a stack size has obviously been too small ;) )

What’s more, if you give too small a stack size, your emulator can crash because Android OS is not meant to support 1kB of a stack size ;)
You need to experiment with a value that suites your needs.

## Caching issue

The good way is to start in the debug mode and catch the exception before it appears.

### Setting up a breakpoint
So first, setup a breakpoint for StackOverflowError on Android Studio.

1. In the menu choose *Run* -> *View Brakepoints...* or *CMD+Shift+F8*
2. Click *+* -> *3. Jave Exception Brakepoints*
3. Search for `StackOverflowError`
4. Click *OK*
5. Click *Done*

Now, you have a breakpoint set.

### Running application

Run your application in the debug mode and click until your exception will catch.

### Looking for important information

When a breakpoint will catch your issue you can easily navigate through the entire stack and find useful information.

## References
* https://stackoverflow.com/questions/16843357/what-is-the-android-ui-thread-stack-size-limit-and-how-to-overcome-it
* https://stackoverflow.com/questions/7453336/setting-stack-size-to-thread-seems-to-make-no-difference-in-android
* http://blog.csdn.net/lhf_tiger/article/details/17719955