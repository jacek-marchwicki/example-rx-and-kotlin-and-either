# Errors... oh... those errors

![The man fell into the chewing gum](error-handling/cover.jpg)

In my opinion, good error handling is one of the most important features of the application.
But most people in development process ignore them:
- Product owner focuses on happy path ignoring issues like missing an internet connection.
- UI/UX designers focus on product owner satisfaction and they don't have good technical knowledge.
- Developers have thousands of tasks fulfill, and none of the tasks are called "error handling".
- And testers...

Yes... sadly, most of the companies find issues in error handling at this point, where a tester starts doing theirs work or users have installed the application from a store.

# Frustration

So let's start with an example:
Your app is an e-mail application, it will have a list of emails and some fields so a user can send email using the send button. 

Because everyone did the job very well implementing features but forgot about error handling, there are possible situations that might happen:

1. A QA tester rejects a task with a comment "I clicked on send and nothing had happened". 
   Your testing team is very good, so a tester will also attach a video and application's logs.
2. You sent an application to the product owner and he/she will say: "I just got a version without sending e-mails implemented"
3. An application is in store and some user said that "I can't click on send button"
4. A user is frustrated because they thinks that they aren't smart enough to use the application and they don't want to contact you.

Now everyone becomes frustrated:
- A tester becomes frustrated because the feature does not work as expected.
- A product owner is frustrated because the feature is not delivered but the task stands that it's working.
- A user is frustrated because the application that he/she uses doesn't work, or he/she is frustrated because he/she doesn't know how to use the app.
- A mobile developer is frustrated because he/she doesn't know what happened and he/she doesn't know how to fix it. 
  He/She also needs to spend additional hours resolving the issue, that might not be an issue.
- a backend developer is frustrated because the mobile developer accuses him/her that this is backend issue.
- An investor is very angry because he/she losing clients, time and money.
- A UI/UX will be angry because everyone will say that this was his/her fault.

# Retrospection

Now, it's time for a retrospection.

The scrum master or the project owner starts the discussion about what happened. 
Developers (mobile/server) didn't change anything and everything started working. 
The tester, product owner, and users don't see the problem anymore.
Problem solved! - But wait... good PM will continue to push the team to find the solution to the future problems like these.

So they will brainstorm to find what might have happened:
1. There were temporary problems with the connection to database,
2. The Europe-America fiber had some temporary outage,
3. There was a deploy during that time,
4. There is some ugly bug in production,
5. The mobile app has some bug in the version that is in the store,
6. The device was connected to some network that didn't have an internet access (like wifi without accepting network agreement),
7. A client was disconnected from wifi,
8. The device has some problems with network,
9. etc...

And guess what?... No-one will know which problem occurred. Moreover, no-one will know how to address such issues.

What's more, someone from the team realized that we can show a message to users "Oops, something wrong has happened." if some issue occurred.
This is a good starting point but doesn't help to understand which problem will appear.

# Solution

**Error handling should distinguish problems and help to resolve issues. Errors are not only for the end user but also for developers.**

1. If a user sees an error, he/she should know what to do next.
2. If a tester sees an error, he/she should know how he/she should fill an issue.
3. If a developer sees an error, he/she should know how to fix it.

Remember, that the production version of the application should also contain errors that are helpful for a developer.
In our example, if a user sent screenshot with error: "Oops, Temporary server outage (502)" and a developer 
will see it, he/she will instantly know that the problem was related to the deployment of production server that took too much time - and there is a place for improvement.
You also can send back quick response to a user, informing what problem arose and you can promise that this was temporary.

## Error types

So let's start with basic errors that we implement and why they are important:

1. *Loading...* - the most important feedback for a user that something is loading. 
   A user needs to know that there is nothing to worry about, just that the content is loading. 
   If loading takes too much time, he/she might think about changing his/her network provider.
   During the loading, without indicator in the e-mail app, a user might think: "All my e-mails were deleted".
2. *No Internet, check your connection* - this is the second most important feedback for a user.
   No Internet is a common situation. On the train, plane, tunnel, underground station, or even some places in a flat.
   Without this message, a user might think that an app has broken or he/she doesn't know how to do things.
   Show a user that it's neither his nor your fault. Show him/her that he/she can fix this problem.
3. *Your password is wrong*, *Title should be filled* - This is definitely frustrating to a user if he/she 
   clicks the send button and doesn't know why he/she can't send an e-mail - he/she just forgot to type recipients.
   People are imperfect and we tend to forget about something. So if a user will forget to fill something, you should help him/her.
4. *Problem with network connection* - (optional but useful) 
   An app can distinguish missing network issues reported by device operating system from exceptions thrown while fetching data (like an unknown host, a connection is broken etc.).
   These types of errors do not precisely mean that a problem is with a client or server network. The good practice is to distinguish these two, so a developer can guess what is wrong. 
5. *No friends, add them using + icon*, *No messages, you can create new ones* - Sometimes when the list is empty, users think that something went wrong. 
   It's a good idea to show a placeholder with a suggestion how he/she should react to change this state.
6. *Temporary server downtime, try again in couple of minutes* - Server updates happen from time to time, even if usually you have updates without downtime. 
   It's a good idea to have some information for a user and for you that server is currently deploying. Usually, servers tend to return 502  HTTP error code in such situations.
   Your client will be happy because he/she knows that an issue will be resolved in minutes. 
   You will be happy because the QA team will not reject your task, they'll ask the backend team if there is an ongoing deploy on staging.
   You will be happy during development because you will know that an app didn't break, just a dev team doing a deployment.
7. *Temporary server issue (500)* - This message should be displayed when you get some 5xx response code from a server.
   This issue should never happen but if it does appear, your QA team will fill a ticket t theo backend team.
   If a user will send you a screenshot of the bug, you will know better what went wrong.
   The mobile team will be happy because they will not be disturbed.
   The backend team will be happy because they will get a better understanding what issues might appear.
8. *Unknown application error (422)* - This message should be displayed when you get some 4xx response code from a server.
   This issue also should never happen but this time the QA team will assign it to the mobile team.
   Also this time, you will better understand users' problems.
   This time the mobile team will know how to handle the problem and the backend team will be safe.
9. *Post not found*, *Not found* - It's also a good practice to implement this specialized error to show the smarter message.
   The user will know how to handle such situations and will know what to do.


## Errors persistence

There is also one more thing. Apps tend to show toast/snack bars with errors.  It's better than no errors but toasts automatically pass away without solving an issue.

Let's show this by example:
1. you open an e-mail application,
2. there is a problem with the Internet so e-mails couldn't load and snack bar is displayed,
3. but you didn't see a snack bar because you talked with a friend,
4. the snack bar disappeared after 2 seconds,
5. you look at your e-mail application and you see nothing and you become very angry because you think someone has hacked into your e-mail account and deleted all of your emails,
6. also this screenshot of the screen will be sent to the development team.

Wouldn't it be much better to show this persistent error until emails will load with success? It wouldn't cause such problems.

But snack bars aren't so wrong. If you pull-to-refresh your emails snack bar will be much better than persistent error. If refreshing will fail you can still show old emails with a small snack bar that will not cover user content.

Also, a snack bar is pretty good for errors about liking, commenting, sending data, even in login when a user is aware that content didn't send and when he is quickly able to retry.

## Disturbing errors

Errors shouldn't make a user work harder.

1. content shouldn't be overlapped by errors,
2. a snack bar shouldn't overlap action buttons,
3. you shouldn't use errors as dialogs because they disrupt users' workflow. Only in a very critical situation, you can show errors as dialog,
4. errors shouldn't block other parts of UI. A user could use other app features during this outage time.


# Summary

- Learn from others' mistakes - do error handling TODAY, not tomorrow.
- Catch as many errors as you can.
- Weak error handling is better than none.
- Error handling should be as easy to implement as it can be to ALL features.
- If you show an error message that will help your user resolve a problem, you will not have his/her problems on your shoulders.
- If you show an error message that will help a QA team find a bug, you will spend less time on finding issues.
- If you show an error message that will help you, you will spend less time debugging the app.
- If you show an error message that will help everyone, everyone will be happy.
- Error handling should distinguish problems and help to resolve issues.
- Errors are not only for an end user but also for developers.

# What's more

* The example how to show errors using Kotlin on Android is presented in the next article - [Errors... oh... those errors - coding](error-handling-coding.md)

# Authors

Author:
* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)