package com.stephentu.util;

public final class Tuple2<T1, T2> {
  public final T1 _1;
  public final T2 _2;
  public Tuple2(T1 _1, T2 _2) {
    this._1 = _1;
    this._2 = _2;
  }
  @Override
  public String toString() {
    return String.format("(%s, %s)", _1, _2);
  }
  @Override
  public int hashCode() {
    return _1.hashCode() ^ _2.hashCode();
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Tuple2<?, ?>)) return false;
    Tuple2<?, ?> that = (Tuple2<?, ?>) o;
    return _1.equals(that._1) && _2.equals(that._2); 
  }
}
