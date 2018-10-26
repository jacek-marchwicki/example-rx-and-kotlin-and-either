# Errors... oh... those errors

![The man fell into the chewing gum](error-handling/cover.jpg)

In my opinion, good error handling is one of the most important features of an application. 
But most people in development process ignore them:
- Product owner focuses on happy path, ignoring issues like missing Internet connection.
- UI/UX designers focus on product owner satisfaction 
- UI/UX designers don't have good technical knowledge.
- Developers have thousands of tasks to fulfill, and none of them is called "error handling".
- And testers...

Yes... sadly, most of the companies lack of error handling becomes a problem, when testers start doing their work or, even worse, upon user’s installation from a store.

# Frustration

So let's start with an example: Your app is an e-mail application, which will have a list of e-mails and some fields so a user can send a message using a send button.

Because everyone did the job of implementing features very well but forgot about error handling, the following might happen:
1. A QA tester rejects the task with a comment "I clicked on send and nothing happened". 
   Your testing team is very good, so the tester will also attach a video and application's logs.
2. You send the application to the product owner and he/she says: "I just got a version without sending e-mails implemented"
3. The application is in the store and a user says "I can't click on send button"
4. Users are frustrated because they think that they aren't smart enough to use the application and they don't want to contact you.

Now everyone becomes frustrated:
- The tester becomes frustrated because the feature doesn’t work as expected.
- The product owner’s frustrated because the feature’s not delivered but the task status is “completed”.
- The user’s frustrated because the application that he/she uses doesn't work, or he/she is frustrated because he/she doesn't know how to use the app.
- The mobile developer is frustrated because he/she doesn't know what happened and he/she doesn't know how to fix it. He/She also needs to spend additional hours resolving the issue that might not be an issue.
- The back-end developer’s frustrated because the mobile developer imputes that this is server issue.
- The investor’s very angry because he/she is losing clients, time and money.
- The UI/UX will be angry because everyone will say that this was his/her fault.

# Retrospection

Now, it's time for retrospection.

The scrum master or the project owner starts the discussion about what happened. Developers (mobile/server) didn't change anything and everything started working. The tester, product owner, and users don't see the problem anymore. Problem solved! - But wait... good PM will continue to push the team to find the solution to the future problems like these.

So they will brainstorm to find out what might have happened:
1. There were temporary problems with the connection to database,
2. The Europe-America fiber had some temporary outage,
3. There was a deploy during that period,
4. There is some ugly bug in production,
5. The mobile app has some bug in the version that is in the store,
6. The device was connected to a network that didn't have Internet access (like public wifi without user’s acceptance of network agreement),
7. A user was disconnected from wifi,
8. The device has some problems with network,
9. etc...

And guess what?... No one will know which of these problems occurred. Moreover, no one will know how to address such an issue, now and in the future.

What's more, someone from the team realized that we can show users a message "Oops, something went wrong." if an issue occurs.. This is a good starting point but doesn't help to understand which problem we’re dealing with.

# Solution

**Error handling should consist in distinguishing problems from one another and helping to resolve issues. Errors aren’t only for end users but also for developers.**

1. If a user sees an error, he/she should know what to do next.
2. If a tester sees an error, he/she should know how to report an issue.
3. If a developer sees an error, he/she should know how to fix it.

It’s worth to have error messages that are helpful for the developer not only in the debug version, but also in the final product. In our example, if a user sends a screenshot with "Oops, Temporary server outage (502)" error and a developer will see it, he/she will instantly know that the problem was related to the deployment of production server - and there we have room for improvement. You can also send back a quick response to the user in which you inform what the problem was and assure that it was only temporary.

## Error types

So let's start with basic error messages that we implement and why they are important:

1. **Loading...** - the most important feedback for a user that something is loading. A user needs to know that there is nothing to worry about, just that the content is being fetched from the Internet. If loading takes too much time, he/she might think about changing his/her network provider :) Without any progress indicator in the e-mail app, when no e-mails are yet displayed to the user due to loading, he might think: “All my e-mails were lost”.
2. **No Internet, check your connection** - this is the second most important feedback for a user. Lack of Internet access is a common situation. On the train, plane, in the tunnel, at the underground station, or even some places in a flat. Without this message, a user might think that an app has broken or he/she doesn't know how to do things. Show a user that it's neither his nor your fault. Show him/her that he/she can fix this problem.
3. **Your password is wrong**, **Title should be filled** - It is definitely frustrating to a user if he/she clicks the send button and doesn't know why he/she can't send an e-mail - he/she just forgot to type in recipients. People are imperfect and we tend to forget about things. So if a user forgets to fill something in, you should help him/her out.
4. **Problem with network connection** - (optional but useful) An app can distinguish missing network reported by the device’s operating system from connection issues while fetching data (like problem accessing your server, broken connection, DNS server issue, etc.). These types of errors do not precisely mean that a root of the problem is a client or server network. The good practice is to distinguish them from “No Internet” error, so a developer can guess what is wrong.
5. **No friends, add them using + icon**, **No messages, you can create new ones** - Sometimes when the list is empty, users think that something went wrong. It's a good idea to show a placeholder with a suggestion how he/she should react to change this state.
6. **Temporary server downtime, try again in a couple of minutes** - Server updates happen from time to time. Even If you usually have updates without downtime, it's a good practice to have an error message that server is currently deploying. Usually, servers tend to return 502 HTTP error code in such situations. Your client is happy because he/she knows that an issue will be resolved within minutes. Developer is happy because the QA team isn’t going to reject his/her task, but ask the back-end team if there is an ongoing deployment. You are happy during development because you know that an app hasn't broken down, just a dev team doing a deployment.
7. **Temporary server issue (500)** - This message should be displayed when you get  a 5xx response code from a server. This should never happen but if it does , your QA team will fill a ticket to the back-end team. If a user  sends a screenshot of the bug, you know better what went wrong. The mobile team’s  happy because they’re not disturbed. The back-end team is  happy because it’s easier for them to diagnose the problem.
8. **Unknown application error (422)** - This message should be displayed when you get a 4xx response code from a server. This  should never happen either.  This time, the QA team  assigns it to the mobile team. Again, you get a better understanding of users' problems. The mobile team knows how to handle them and the back-end team stays safe.
9. **Post not found**, **Not found** - It's also a good practice to implement this specific error as the user knows how to handle such situations.

## Errors persistence

There is one more thing. Apps tend to show [toasts/snack](https://developer.android.com/guide/topics/ui/notifiers/toasts) bars with error notification. It's better than no error messages but toasts automatically disappear without solving an issue.

Let's show this by example:
1. you open an e-mail application,
2. there’s a problem with the Internet connection so e-mails can't be downloaded and a snack bar is displayed,
3. you don’t see a snack bar appear because you‘re talking to a friend,
4. the snack bar disappears after 2 seconds,
5. you look at your e-mail application, see nothing and become very angry because you think someone has hacked into your e-mail account and deleted all of your precious e-mails,
6. the screenshot of the empty inbox is sent to the development team along with a complaint.

Wouldn't it be much better to display an error message incessantly until e-mails load? It wouldn't cause such problems.

But snack bars aren't always such a wrong solution. If you pull-to-refresh your e-mails, snack bar will be much better than a persistent error message. If refreshing fails, you can still see old e-mails with a small snack bar that will not cover user content.

Also, a snack bar’s pretty useful for errors about liking, commenting, creating posts and during logging in; when a user’s aware that an action didn't succeed and when he’s able to retry instantly.

## Disturbing errors

Errors shouldn't make user’s work harder.
1. content shouldn't be overlapped by errors,
2. a snack bar shouldn't overlap action buttons,
3. you shouldn't use error messages as dialog windows because they disrupt users' workflow. Only in a very critical situation, when user’s data might be lost, can you show errors as dialog windows,
4. Error messages shouldn't block other parts of UI. A user should be able to  use other app features during the outage time.

# Summary

- Learn from others' mistakes - do error handling TODAY, not tomorrow.
- Catch as many errors as you can.
- Weak error handling is better than none.
- Error handling should be as easy to implement as possible , no matter the feature.
- If you display an error message that helps your user resolve a problem, you will not have his/her problem on your shoulders.
- If you display an error message that helps a QA team find a bug, you will spend less time on finding causes of the issues.
- If you display an error message that helps you, you will spend less time debugging the app.
- If you display an error message that helps everyone, everyone will be happy.
- Error handling should distinguish between problems and help resolve issues.
- Error messages aren’t meant only for end users but also for developers.

# What's more

* An example of how to display error messages using Kotlin on Android is presented in the next article - [Errors... oh... those errors - coding](error-handling-coding.md)

# Authors

* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)