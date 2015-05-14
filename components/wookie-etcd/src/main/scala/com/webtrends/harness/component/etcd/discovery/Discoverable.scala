package com.webtrends.harness.component.etcd.discovery


trait Discoverable extends EtcdHelper {

}

case class Broadcast(path:String, value:AnyRef) extends Discoverable {

}
