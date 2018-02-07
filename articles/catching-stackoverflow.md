# How to find StackOverflowError's

## Abstract
Sometimes you see StackOverflowError's on some devices. Some of them you can't reproduce on your device or those crashes appear in unexpected situations. There are some tips that will allow finding those issues easier.

## Description.
Usually, StackOverflowError appears unexpected and sometimes it's hard to trace them with just stack trace that you will get from a crash on your device or from a crash on your crash reporter. On Android, different devices have different stack size depending on API level or even manufacturer. So the good way is to easily catch StackOverflowError is to decrease stack trace on an emulator.

## Decreasing stack size
Run Dalivik emulator (before API level 5, I didn’t check this solution on ART emulators). Now you need to decrease stack size:

```bash
adb root
adb shell stop
adb shell setprop dalvik.vm.extra-opts -Xss10280
adb shell start
```

Now you emulator will restart. You neet to look on your logcat for errors like this: 
```
I/vm-printf: Invalid -Xss '-Xss1', range is 1280 to 262912
I/vm-printf:   -XssN  (stack size, >= 1KB, <= 256KB)
```

If you see such issue it means that you gave too small or too large stack size (In this case `-Xss1` - One byte of stack size was obviously too small ;) )

Also if you give too small stack size your emulator can crash because Android OS is not to be meant to support 1kB of stack size ;)
You need to experiment with a value that suites for your needs.

## Caching issue

The good way is to start in debug mode and catch the exception before it appears.

### Setting up a breakpoint
So at start setup breakpoint for StackOverflowError on Android Studio.

1. From menu choose *Run* -> *View Brakepoints...* or CMD+Shift+F8
2. Click *+* -> *3. Jave Exception Brakepoints*
3. Search for `StackOverflowError`
4. Click *OK*
5. Click *Done*

Now you have breakpoint set.

### Running application

Run your application in debug mode and click until your exception will catch.

### Looking for important information

When breakpoint will catch your issue you can easily navigate through entire stack and find useful information.

## References
* https://stackoverflow.com/questions/16843357/what-is-the-android-ui-thread-stack-size-limit-and-how-to-overcome-it
* https://stackoverflow.com/questions/7453336/setting-stack-size-to-thread-seems-to-make-no-difference-in-android
* http://blog.csdn.net/lhf_tiger/article/details/17719955