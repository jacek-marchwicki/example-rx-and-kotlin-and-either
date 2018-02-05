# Errors

In my opinion good error handling is one of the most important feature of the application.
But most people in development process ignore them.
- Product owner focus on happy path ignoring issues like missing internet connection.
- UI/UX designers focus on product owner satisfaction and they don't have good technical knowledge.
- Developers have thousands of tasks fulfill, and none of tasks is called "error handling".
- And testers...

Yes... sadly, most of companies find issues in error handling at that point, were tester start doing his work.

So let start with an example:
Your app is a guest book that will have one field and send button so a user is able to send new entries. 
All entries from all users should be displayed as a list.

Because everyone did they job very well but ignored error handling, there are probable situations that might appear:

1. Task is rejected with comment "I clicked on send and nothing had happened". 
   Your testing team is very good, so tester will also attach video. and add full application logs.
2. You sent application to product owner and he sad that "sending is not yet implemented"
3. Application is in store and some user said that "button send does not work"
4. User is frustrated because he thing that he done something wrong and he don't want to admit to you.

Now everyone become frustrated:
- Tester become frustrated because feature does not work as expected.
- Product owner is frustrated because feature is not delivered but task stands that it's working.
- User is frustrated because application that he use doesn't work or he is frustrated because he don't know how to use app.
- Mobile Developer is frustrated because he don't know what has happened and he don't know how to fix this issue. 
  He also need to spend additionall hours resolving issue, that might not be and issue.
- Backend Developer is frustrated because mobile developer accuse him that is his fault.
- Investor is very angry because he losing clients, time and money.
- UI/UX will be angry later ;)

Now it's time for retrspection.

Scrum master or project owner start discussion about what has happened.
Developers didn't changed anything and everything working on they sides (mobile/server). 
Tester, product owner and users doesn't see the problem anymore.
Problem solved! - Good PM will continue to push the team to find solution for future problems like this.

So they will try to find what could happened:
1. There were temporary problem with server,
2. Amazon had some temporary outage,
3. There were deploy during that time,
4. There is some ugly bug in production,
5. Mobile app has some bug in version that is in the store,
6. Device was connected to some network that didn't have internet access (like wifi without accepting network aggrement)
7. Client was disconnected from wifi
8. Device have some problems with network

And guess what... None will know which problem occured. Moreover none will know how to solve such issues.

Also someone from the teem said, we can show message to user "Oops, something wrong has happened." when issue appear.
This is a good staring point but doeson't help to understand which problem appeared.

# Good error handling

**Error handling should distingush problems and help resolving issues**

Errors are not only for end user, but also for developers.
1. If user see error, he should now what to do next.
2. If tester see error, he should now how should fix it.
3. If developer see error, he should now how to fix it.

Remember that production version of the application should also contain errors that are helpfull for developer.
In our example if user would send screenshot with error: "Oops, Temporary server outage (502)" and developer 
will see he will know that problem was related to deployment of production server that took too much time - and there is a place for improvments.
You also can send back to user quick response what issue has gone and you can promise that issue was temporary.

So let's start with basic errors that we implement and why they are important:
1. *Loading...* - most important feedback for user that something is loading. 
   User needs that nothing need to worry about, just content is loading. 
   If loading takes time he might thinking about changing his network provider.
   Without loading indicator in e-mail app user might think: "All my e-mail were lost".
2. *No internet, check your connection* - this is the second most important feedback for user.
   No internet is common situation. In a train, plane, tunnel, undrground, some place in the flat.
   Without this message user might think that app has broken or he don't know how to do things.
   Show to user that it's not his or your fault. Show to him that he can fix this issue.
3. *Your password is wrong*, *Title should be filled* - This is definetly frustrating to user if he 
   click send button and does not know why message can't send e-mail, he just forgot to type recepient.
   People are imperfect and we are tend to forgot about something. So if user will forgot to fill something 
   you should help him be comfortable with that and help him resolve that issue.
4. 

More problems resolved by client, will give less problems showed to develoeprs.






Toast error vs view error!!! - błędy które są stałe powinny być wyświetlane cały czas by toast nie mógł zniknąć. i nie zrobisz screenthost

But good error handling it's not simple in the code 

It's not my fault

- User use devices in condition does not have internet
- User can join to some kind of wifi hot spot that does not have internet
- User can flacky connection
- User enter wrong data
- Server have temoprary issue
- User have broken device
- Developer's did something wrong and user complain about this.
