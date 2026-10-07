package org.fengshen.dev

/** Stable content identities with a bounded LRU; failed loads never enter the cache. */
class ResourceMap<K:Any,V:Any>(identities:Collection<K>,private val capacity:Int,
    private val load:(K)->V):AbstractMap<K,V>() {
    private val ids=identities.toSet()
    private val cache=java.util.LinkedHashMap<K,V>(capacity,.75f,true)
    private val weights=mutableMapOf<K,Long>()
    private var byteLimit=Long.MAX_VALUE
    private var weightOf:(V)->Long={0L}
    private var retainedBytes=0L
    init {require(capacity>0);require(ids.size==identities.size)}
    /** Optional image budget; keep the published three-argument constructor. */
    @Synchronized fun limitBytes(limit:Long,weight:(V)->Long):ResourceMap<K,V> {
        require(limit>0&&cache.isEmpty())
        byteLimit=limit;weightOf=weight;return this
    }
    override val size get()=ids.size
    override val keys:Set<K> get()=ids
    override fun containsKey(key:K)=key in ids
    @Synchronized override fun get(key:K):V? {
        if(key !in ids)return null
        cache[key]?.let{return it}
        val value=load(key)
        val weight=weightOf(value)
        require(weight>=0&&weight<=byteLimit){"Resource exceeds its cache byte budget"}
        // Evict before addition to avoid overflow even for a very large budget.
        while(cache.isNotEmpty()&&(cache.size>=capacity||retainedBytes>byteLimit-weight)){
            val first=cache.entries.first().key
            cache.remove(first);retainedBytes-=weights.remove(first)!!
        }
        cache[key]=value
        weights[key]=weight;retainedBytes+=weight
        return value
    }
    override val entries:Set<Map.Entry<K,V>> get()=object:AbstractSet<Map.Entry<K,V>>() {
        override val size get()=ids.size
        override fun iterator():Iterator<Map.Entry<K,V>> {
            val source=ids.iterator()
            return object:Iterator<Map.Entry<K,V>> {
                override fun hasNext()=source.hasNext()
                override fun next():Map.Entry<K,V> {
                    val key=source.next();val value=this@ResourceMap.getValue(key)
                    return object:Map.Entry<K,V>{override val key=key;override val value=value}
                }
            }
        }
    }
    @Synchronized fun cachedKeys():Set<K> =cache.keys.toSet()
    @Synchronized fun cachedBytes():Long =retainedBytes
}
