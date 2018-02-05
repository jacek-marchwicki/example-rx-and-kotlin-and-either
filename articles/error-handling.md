# Errors... eh those errors

In my opinion good error handling is one of the most important feature of the application.
But most people in development process ignore them:
- Product owner focus on happy path ignoring issues like missing internet connection.
- UI/UX designers focus on product owner satisfaction and they don't have good technical knowledge.
- Developers have thousands of tasks fulfill, and none of tasks is called "error handling".
- And testers...

Yes... sadly, most of companies find issues in error handling at that point - were tester start doing his work or users installed application from store.

# Frustration

So let start with an example:
Your app is an e-mail application, it will have list of emails and some fields so user can send email using send button. 

Because everyone did they job very well implementing features but forgot about error handling, there are probable situations that might happen:

1. QA tester reject task with comment "I clicked on send and nothing had happened". 
   Your testing team is very good, so tester will also attach video and applications logs.
2. You sent application to product owner and he will say: "I just get a version without sending e-mails implemented"
3. Application is in store and some user said that "I can't click on send button"
4. User is frustrated because he think that he are not smart enough to use your application and he don't want to contact you.

Now everyone become frustrated:
- Tester become frustrated because feature does not work as expected.
- Product owner is frustrated because feature is not delivered but task stands that it's working.
- User is frustrated because application that he use doesn't work, or he is frustrated because he don't know how to use app.
- Mobile Developer is frustrated because he don't know what had happened and he don't know how to fix it. 
  He also need to spend additional hours resolving issue, that might not be and issue.
- Backend Developer is frustrated because mobile developer accuse him that this is backend issue.
- Investor is very angry because he losing clients, time and money.
- UI/UX will be angry because everyone will say that this was his fault.

# Retrospection

Now it's time for retrospection.

Scrum master or project owner start discussion about what had happened. 
Developers (mobile/server) didn't changed anything and everything started working. 
Tester, product owner and users doesn't see the problem anymore.
Problem solved! - But wait... good PM will continue to push the team to find solution for a future problem like this.

So they will brainstorm to find what could happened:
1. There were temporary problem with connection to database,
2. Europe-America fiber had some temporary outage,
3. There were deploy during that time,
4. There is some ugly bug in production,
5. Mobile app has some bug in version that is in the store,
6. Device was connected to some network that didn't have internet access (like wifi without accepting network aggrement),
7. Client was disconnected from wifi,
8. Device have some problems with network,
9. etc...

And guess what... None will know which problem occured. Moreover none will know how to solve such issues.

Also someone from the team realized that we can show message to user "Oops, something wrong has happened." if some issue happen.
This is a good staring point but doesn't help to understand which problem'll appear.

# Solution

**Error handling should distinguish problems and help resolving issues. Errors are not only for end user, but also for developers.**

1. If user see error, he should know what to do next.
2. If tester see error, he should know how should fill issue.
3. If developer see error, he should know how to fix it.

Remember that production version of the application should also contain errors that are helpful for developer.
In our example if user would send screenshot with error: "Oops, Temporary server outage (502)" and developer 
will see he will instantly know that problem was related to deployment of production server that took too much time - and there is a place for improvements.
You also can send back to user quick response what issue has gone and you can promise that issue was temporary.

## Error types

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
4. *Problem with network connection* - (optional but useful) 
   You can destinguish missing network issues reported by device operating system from exceptions thrown while feaching data (like Unknown host, connection broken etc.).
   Those types of errors does not preciseslly mean that there is problem with client or server network so it's good to distingush those two so developer can gues what is wrong. 
4. *No friends, add them using + icon*, *No messages, you can create new one* - Sometimes when list is empty users think that something is wrong. 
   It's good idea to show some placeholder with suggestion what he should do to change this state.
5. *Temporary server downtime, try again in a minutes* - Server updates happen from time to time, even if normally you have updates without downtime. 
   It's good idea to have some information for user and for you that server is currently deploying. Usually servers tend to return 502 response in such situations.
   Your client will be happy because he know that issue will be resolved in a minutes. 
   You will be happy because QA team will not reject your task but ask on slack backend team if there is a deploy on staging.
   You will be happy because you will know that you didn't broken an app but dev team doing deploy.
6. *Temporary server issue (500)* - Message should be displayed when you get some 5xx response code from server.
   This issue should never happen but if it will appear your QA team will fill a ticket to backend team.
   If user will send you a screenshot about bug you will know better what gone wrong.
   Mobile team will be happy because they will not be disturb.
   Backend team will be happy because will get better understanding what issue might appear.
7. *Unknown application error (422)* - Message should be displayed when you get some 4xx response code from server.
   This issue also should never happen but this time QA team will assign it to mobile team.
   Also this time you will better understand users problems.
   This time mobile team will know how to handle problem and backend team will be safe.
9. *Post not found*, *Not found* - Also it's good practice to implement this specialized error to show smarter message.
   User will know how to handle such situation and will know what to do.


## Errors persistence

There is also one more thing. Apps tend to show toast/snackbars with errors. 
It's better than none errors but toasts automatically passing away without solving an issue.
I.e.
1. you open e-mail application;
2. there is a problem with internet so e-mails couldn't load and snackbar is displayed;
3. but you didn't saw snackbar because you talked with friend;
4. snackbar disapear after 2 seconds;
5. you look on your e-mail application and you see nothing and you becomeing very angry because you think someone hacked to your e-mail account and deleted all of your mails.

Wouldn't be much better to show this error until e-mail loads? It would be much better.

But snackbar aren't so wrong. If you pull-to-refresh your emails snackbar will be much better than persistent error. 
If refreshing will fail you can still show your old e-mails with small snackbar with information why refreshing failed.

Also snackbar is pretty good for errors about liking, commenting, sending data, even in login when user is aware that content didn't sent and when he is quickly able to retry.

## Disturbing errors

Also errors shouldn't making user work harder.

1. Views Shouldn't overlap content.
2. Snackbar shouldn't overlap action buttons.
3. You shouldn't use errors as dialogs because they disrupt users workflow. Only in very critical situations you can show dialogs.
4. Errors shouldn't block other parts of UI. User could use other app features during this outage time.


# Summarise

- Learn on others mistakes - do error handling NOW, not tomorrow.
- Catch as many errors as you can.
- Better is week error handling than none.
- Error handling should be as easy to implement as it can be to ALL features.
- If you show error message that will help your user resolve problem, you will not have his problems on your shoulders.
- If you show error message that will help QA team find bug, you will spend less time on finding issues.
- If you show error message that will help you, you will spend less time debugging app.
- If you show error message that will help everyone, everyone will be happy.
- Error handling should distinguish problems and help resolving issues.
- Errors are not only for end user, but also for developers.

# What's more

* Example how to show errors using kotlin will be in next article