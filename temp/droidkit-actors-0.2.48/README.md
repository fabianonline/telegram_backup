DroidKit Actors
===============
Lightweight java implementation of actor model for small applications. Designed for Android applications.
Read more about actors on [Wikipedia](http://en.wikipedia.org/wiki/Actor_model)

QuickStart
===============
### Add dependency to your gradle project
```
compile 'com.droidkit:actors:0.1.+'
```

### Create log Actor
```
import android.util.Log;
import com.droidkit.actors.Actor;

public class LogActor extends Actor {

    @Override
    public void preStart() {
        Log.d("LOGACTOR#" + hashCode(), "preStart");
    }

    @Override
    public void onReceive(Object message) {
        Log.d("LOGACTOR#" + hashCode(), message + "");
    }

    @Override
    public void postStop() {
        Log.d("LOGACTOR#" + hashCode(), "postStop");
    }
}
```

### Use main actor system
Actor system is entry point to actor model, it contains all configurations, dispatchers and actors.
Dispatcher is a queue + worker threads for this queue.

By default ActorSystem has static main ActorSystem and in most cases you can use it in two ways:
```
void a() {
  ActorSystem.system()
}
```
or
```
import static com.droidkit.actors.ActorSystem.system;

void a() {
  system()
}
```
### or create your Actor system
```
ActorSystem system = new ActorSystem();
// Add additional dispatcher with threads number == cores count
system.addDispatcher("images");
// Add additional dispather with 3 threads with minimal priority
system.addDispatcher("images", new MailboxesDispatcher(system, 2, Thread.MIN_PRIORITY));
```
### Complete sample
```
system().addDispatcher("images", new MailboxesDispatcher(system(), 2, Thread.MIN_PRIORITY));

ActorRef log1 = system().actorOf(LogActor.class, "log");
ActorRef log2 = system().actorOf(LogActor.class, "log");
ActorRef log3 = system().actorOf(Props.create(LogActor.class).changeDispatcher("images"), "log2");
ActorRef log4 = system().actorOf(Props.create(LogActor.class).changeDispatcher("images"), "log3");

ActorRef[] refs = new ActorRef[]{log1, log2, log3, log4};
for (int i = 0; i < 100; i++) {
    refs[i % refs.length].send("test" + i);
}
```
Log output will be with messages without ordering across all messages, but ordered for every actor.

License
===============
License use [MIT License](LICENSE)
