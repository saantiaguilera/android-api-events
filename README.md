#Event Bus + Sugars!

This project was really old, and as time went by it went being better and better. Until one day otto and eventbus appeared (?. Since I was really used to my own version, and it was a bit older, I never stop using it and continue giving it support.

It was in a lot of projects, (controllers - then camera - then http) and I never took my time to upload it to maven and simply create its own repository. So I have done it now :)

##What is?

Its an event bus, like all the ones you know. It probably works really close to EventBus or Otto (I have no idea I must admit tho). It uses reflection to an extent (you will see later how to receive events), but since we all know reflection can be really costly, I try to cache the ones that are mostly used so we dont have to use reflection all the time and run out of battery in 30 secs.

##How to get it

In your project gradle make sure you have
```Java
allprojects {
	repositories {
		jcenter()
	}
}
```

In your application gradle add 
```Java
dependencies {
  compile 'com.saantiaguilera:EventsLib:1.0.3'
}
```

Since Im still not full live in maven (because bintray hasnt added me yet to jCenter) you have to also add to your application gradle
```Java
repositories {
    mavenCentral()
    maven {
        url  "http://dl.bintray.com/saantiaguilera/maven"
    }
}
```

##Events!!

Create somewhere an instance of an EventBus.
With this you will be able to start suscribing objects to "receive events" and also dispatching events to them!
```Java
eventBus = new EventBus(aContext);
```

If you want a class to start listening to events just
```Java
eventBus.addObservable(something);
//or...
eventBus.removeObservable(something);
```

Now this "something" is able to start receiving Events !! But where does he receives them?

Lets say we have OneEvent and TwoEvent. He can receives them like:
```Java
@EventMethod(OneEvent.class)
private void oneMethod() {
    //Do something
}

@EventMethod(TwoEvent.class)
private void anotherMethod(TwoEvent event) {
    //Do something
}
```
Note: Method can only have either 1 param of the particular Event type or none

Note: When I get more time I will try to support the repeatable anotation since its not available yet
(Kinda like the @RequiresPermission() does)

So... Now we know how to receive events and how to start listening. What about sending one ?
```Java
///Somewhere in a method...
eventBus.dispatchEvent(new OneEvent());
```
And this will alone call all the methods that have its anotation and are observing that eventManager instance.

Also if you want to execute that particular method in another thread just do
```Java
@EventAsync
@EventMethod(SomeEvent.class)
private void whenCallingThisFromADispatchOfAnEventItWillBeAsynchronous() {
  //Do something...
}
```
But if you dispatch another event inside there, be careful that the observables will still execute their code in the main thread!
(Unless they also specified @EventAsync)

Finally, what is SomeEvent ??
```Java
public class SomeEvent extends Event {
    int aParam;
    String anotherParam;

    public SomeEvent(int aParam, String anotherParam) {
      this.aParam = aParam;
      this.anotherParam = anotherParam;
    }

    //getters...
}
```
Its just a subclassification of Event. Although it can have its own logic, ideally it should only be able to carry data from some
place to another.

You also have some other features like "dispatchSticky" which dispatches an event to all the current observables + the new ones that suscribe later to the bus
```Java
eventBus.dispatchEventSticky(new SomeEvent());
```

Or dispatch events delayed (and even dispatch sticky events delayed!).

Note: Event bus also supports overriding. Meaning that if a parent class is "listening" to events of A, and your subclass overrides that method, your subclass will be invoked when an A event is dispatched :)

