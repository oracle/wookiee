#Wookiee - Component Caching

For Configuration information see [Caching Config](docs/config.md)

##Creating a Cache

Before we can create cache objects, we need to do the following:

* Add dependency to service pom
* Enable the CacheManager system component
* Initialize the cache in our service

###Adding dependency to service POM

To add Wookiee caching you need to include the dependency into your maven pom, like below:
```
<dependency>
  <groupId>com.webtrends</groupId>
  <artifactId>wookiee-cache</artifactId>
  <version>${platform.version}</version>
</dependency>
```
This would then download the libraries that allow using the Cacheable Trait. By default this would only give you access to the in memory cache, which is not incredibly useful for most cases. Generally a specific caching module would be used that would depend on the module above. Currently there is a memcache module for use that you can use in your project by adding the following dependency:
```
<dependency>
  <groupId>com.webtrends</groupId>
  <artifactId>wookiee-cache-memcache</artifactId>
  <version>${platform.version}</version>
</dependency>
```
NOTE: If you use the above dependency you do not need the wookiee-cache dependency as it will be automatically used from the wookiee-cache-memcache module, and this should apply for any caching module that may be included later.

###Enable Caching system component

If you wish to use the in-memory caching scheme, then you would enable the component in wookiee-cache.conf:
```
wookiee-cache {
  manager = "com.webtrends.harness.component.cache.memory.MemoryManager"
  enabled = true
}
```
If you wish to use the memcache component, then you would enable the component in wookiee-cache-memcache.conf:
```
wookiee-cache-memcache {
  manager = "com.webtrends.harness.component.memcache.MemcacheManager"
  enabled = true
}
```

Most of the configuration for Memcache itself would be defined by the application and not by Wookiee. Wookiee config simply loads up the manager for the clients, not the clients themselves. 

###Initializing a Cache

To actually be able to get and set to a Cache you need to initialize a cache in the CacheManager. Currently the only cache to use would be the MemcacheManager, which obviously requires Memcache servers to work correctly. Each cache that is initialized can be set to a different Memcache server, or they can all point to the same source, it is completely up to the developer and the requirements of the work. 
The following code snippet would initialize a Memcache Client:
```
// build the cache for the report data
val manager = context.actorSelection("/user/" + classOf[MemcacheManager].getName)
val config = CacheConfig(
  namespace = LookupConstants.MemcacheCacheName,
  props = Some(Map("serverList" -> context.system.settings.config.getString(LookupConstants.KeyMemcacheServer)))
)
manager ! CreateCache(config) // let MemcacheManager handle the exceptions if any
```
Let's go through this line by line.

* Line 1: This calls the MemcacheManager that was loaded as a Actor SystemComponent. This what we will be using to make all our calls for MemCache
* Line 2: Create a CacheConfig object, which holds all relevant information for creating a cache.
* Line 3: Raise an event to the MemcacheManager to create a cache with the config supplied.

Once the cache is supplied you can use the following events against the MemcacheManager:

* CreateCache(config:Config) - Creates a cache with the supplied configuration
* Get - retrieves an object stored in the cache
* Add - Adds an object to the cache
* Delete - Deletes an object from the cache
* Decrement - Creates an integer based object in the cache and decrements the value
* Increment - Creates an integer based object in the cache and increments the value
* Contains - Checks to see whether an object is in the cache
* Clear - Clears the cache

With this you can perform basic functionality against the newly created cache. However if you are dealing with a specific object you are trying to store in the cache, you can more easily manage that through the Cacheable object trait. This will then handle all the serializing and deserializing of the object to and from the cache.

##Creating a Cacheable Object
A cacheable object is simply an object that uses the Cacheable trait from the wookiee-cache library. Once an object is cacheable it provides two methods that you don't need to implement as the base implementations will write to the cache and read from the cache without any further code.
The cacheable object is required to implement two methods:

* key - This function simply returns the key for the object, the cache functions from the cacheable trait allow for sending in the key for each function call, so you can simply return empty string if you don't plan on internalizing the key
* namespace - This is required and should be defined based on the namespace that was supplied to the cache when it was created.

```
def readFromCache(cacheRef:ActorSelection, cacheKey:Option[String])
      (implicit timeout:Timeout, executor:ExecutionContext, m:Manifest[T]) : Future[Option[T]]
```
The readFromCache will then require the reference to the Actor for the CacheManager, which can be either and ActorRef or an ActorSelection object. The cacheKey can be left as NONE if the key for the object is defined correctly by the key function in your cacheable object.
```
def writeInCache(cacheRef:ActorSelection, cacheKey:Option[String])
      (implicit timeout:Timeout, executor:ExecutionContext) : Unit
```
The writeInCache will take the same parameters as readFromCache and simply write the current object to the cache. So there would be a little up front work to find the actor, otherwise you can simply use the Cacheable trait and call these two functions to get and set your object into a cache.

###Data Timeout
By default an object will remain in cache until it is either specifically deleted from the cache, or in the case of Memcache, until the servers are restarted. (The Memcache servers, not Wookiee) 
This is not always the best approach, so another way is to allow the user to set how long the object will remain in the cache until it is evicted. The dataTimeout method returns a Long in milliseconds to define that time. Every time an object is stored in the cache it wraps the object and places the current time into the wrapped object. When the object is retrieved it checks to see whether the objects insertion time is older than the current time minus the timeout value retrieved from the dataTimeout method on the object. If it is the object is rejected and deleted and NONE is returned.
