# Errors... oh... those errors

In my opinion, good error handling is one of the most important feature of the application.
But most people in development process ignore them:
- Product owner focuses on happy path ignoring issues like missing internet connection.
- UI/UX designers focus on product owner satisfaction and they don't have good technical knowledge.
- Developers have thousands of tasks fulfill, and none of the tasks is called "error handling".
- And testers...

Yes... sadly, most of the companies find issues in error handling at that point - were tester start doing his work or users installed the application from a store.

# Frustration

So let start with an example:
Your app is an e-mail application, it will have a list of emails and some fields so the user can send email using send button. 

Because everyone did the job very well implementing features but forgot about error handling, there are possible situations that might happen:

1. QA tester reject task with a comment "I clicked on send and nothing had happened". 
   Your testing team is very good, so tester will also attach video and applications logs.
2. You sent application to product owner and he will say: "I just get a version without sending e-mails implemented"
3. Application is in store and some user said that "I can't click on send button"
4. The user is frustrated because he thinks that he isn't smart enough to use the application and he doesn't want to contact you.

Now everyone become frustrated:
- Tester becomes frustrated because the feature does not work as expected.
- The product owner is frustrated because the feature is not delivered but task stands that it's working.
- The user is frustrated because the application that he uses doesn't work, or he is frustrated because he doesn't know how to use the app.
- Mobile Developer is frustrated because he doesn't know what had happened and he doesn't know how to fix it. 
  He also needs to spend additional hours resolving the issue, that might not be an issue.
- Backend Developer is frustrated because mobile developer accuses him that this is backend issue.
- The investor is very angry because he losing clients, time and money.
- UI/UX will be angry because everyone will say that this was his fault.

# Retrospection

Now it's time for retrospection.

Scrum master or project owner start the discussion about what had happened. 
Developers (mobile/server) didn't change anything and everything started working. 
Tester, product owner, and users don't see the problem anymore.
Problem solved! - But wait... good PM will continue to push the team to find a solution to future problems like this.

So they will brainstorm to find what could happen:
1. There were temporary problem with connection to database,
2. Europe-America fiber had some temporary outage,
3. There were deploy during that time,
4. There is some ugly bug in production,
5. Mobile app has some bug in version that is in the store,
6. Device was connected to some network that didn't have internet access (like wifi without accepting network agreement),
7. Client was disconnected from wifi,
8. Device has some problems with network,
9. etc...

And guess what... None will know which problem occurred. Moreover, none will know how to solve such issues.

Also, someone from the team realized that we can show a message to users "Oops, something wrong has happened." if some issue happen.
This is a good starting point but doesn't help to understand which problem will appear.

# Solution

**Error handling should distinguish problems and help to resolve issues. Errors are not only for end user but also for developers.**

1. If a user sees error, he should know what to do next.
2. If a tester sees error, he should know how should fill issue.
3. If a developer sees error, he should know how to fix it.

Remember that production version of the application should also contain errors that are helpful for developer.
In our example, if user would send screenshot with error: "Oops, Temporary server outage (502)" and developer 
will see he will instantly know that problem was related to the deployment of production server that took too much time - and there is a place for improvements.
You also can send back to user quick response what issue has gone and you can promise that issue was temporary.

## Error types

So let's start with basic errors that we implement and why they are important:

1. *Loading...* - most important feedback for a user that something is loading. 
   The user needs that nothing need to worry about, just content is loading. 
   If loading takes too much time, he might think about changing his network provider.
   During the loading, without indicator in the e-mail app, the user might think: "All my e-mail were deleted".
2. *No internet, check your connection* - this is the second most important feedback for a user.
   No internet is a common situation. In a train, plane, tunnel, underground station, or even some places in a flat.
   Without this message, the user might think that app has broken or he doesn't know how to do things.
   Show to the user that it's not his or your fault. Show to him that he can fix this issue.
3. *Your password is wrong*, *Title should be filled* - This is definitely frustrating to a user if he 
   click send button and doesn't know why he can't send an e-mail - he just forgot to type recipients.
   People are imperfect and we tend to forget about something. So if the user will forget to fill something, you should help him.
4. *Problem with network connection* - (optional but useful) 
   The app can distinguish missing network issues reported by device operating system from exceptions thrown while fetching data (like an unknown host, a connection is broken etc.).
   Those types of errors do not precisely mean if a problem is with client or server network. The good practice is to distinguish those two, so a developer can guess what is wrong. 
4. *No friends, add them using + icon*, *No messages, you can create new one* - Sometimes when the list is empty users think that something went wrong. 
   It's a good idea to show some placeholder with a suggestion what he should do to change this state.
5. *Temporary server downtime, try again in a minutes* - Server updates happen from time to time, even if normally you have updates without downtime. 
   It's a good idea to have some information for a user and for you that server is currently deploying. Usually, servers tend to return 502  HTTP error code in such situations.
   Your client will be happy because he knows that issue will be resolved in minutes. 
   You will be happy because QA team will not reject your task, they'll ask on slack backend team if there is ongoing deploy on staging.
   You will be happy during development because you will know that app didn't break, just a dev team doing a deployment.
6. *Temporary server issue (500)* - Message should be displayed when you get some 5xx response code from a server.
   This issue should never happen but if it will appear your QA team will fill a ticket to backend team.
   If a user will send you a screenshot of the bug you will know better what gone wrong.
   Mobile team will be happy because they will not be disturb.
   Backend team will be happy because will get a better understanding what issue might appear.
7. *Unknown application error (422)* - Message should be displayed when you get some 4xx response code from a server.
   This issue also should never happen but this time QA team will assign it to mobile team.
   Also this time you will better understand users problems.
   This time the mobile team will know how to handle problem and backend team will be safe.
9. *Post not found*, *Not found* - Also it's good practice to implement this specialized error to show the smarter message.
   The user will know how to handle such situation and will know what to do.


## Errors persistence

There is also one more thing. Apps tend to show toast/snack bars with errors.  It's better than none errors but toasts automatically passing away without solving an issue.

Lest show this by example:
1. you open e-mail application;
2. there is a problem with internet so e-mails couldn't load and snack bar is displayed;
3. but you didn't saw snack bar because you talked with friend;
4. snack bar disappear after 2 seconds;
5. you look at your e-mail application and you see nothing and you become very angry because you think someone hacked to your e-mail account and deleted all of your emails.
6. also this screenshot of the screen will be sent to the development team.

Wouldn't be much better to show this persistent error until emails will load with success? It would cause such problems.

But snack bars aren't so wrong. If you pull-to-refresh your emails snack bar will be much better than persistent error. If refreshing will fail you can still show old emails with a small snack bar that will not cover user content.

Also, a snack bar is pretty good for errors about liking, commenting, sending data, even in login when a user is aware that content didn't send and when he is quickly able to retry.

## Disturbing errors

Errors shouldn't make a user work harder.

1. Content shouldn't be overlapped by errors.
2. Snack bar shouldn't overlap action buttons.
3. You shouldn't use errors as dialogs because they disrupt users workflow. Only in a very critical situation, you can show error as dialog.
4. Errors shouldn't block other parts of UI. A user could use other app features during this outage time.


# Summarise

- Learn from others mistakes - do error handling NOW, not tomorrow.
- Catch as many errors as you can.
- Better is week error handling than none.
- Error handling should be as easy to implement as it can be to ALL features.
- If you show an error message that will help your user resolve problem, you will not have his problems on your shoulders.
- If you show an error message that will help QA team find a bug, you will spend less time on finding issues.
- If you show an error message that will help you, you will spend less time debugging the app.
- If you show an error message that will help everyone, everyone will be happy.
- Error handling should distinguish problems and help to resolve issues.
- Errors are not only for an end user but also for developers.

# What's more

* Example how to show errors using Kotlin on Android will be presented in next article