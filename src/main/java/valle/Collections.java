package valle;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Collections
{
    public static String[] trim(String[] tokens) {
        ArrayList<String> out = new ArrayList<>();
        for ( String token : tokens )
        {
            token = token.strip();
            if(token.isEmpty()) {
                continue;
            }
            out.add( token );
        }
        return out.toArray( new String[0] );
    }

    public static <T> T[] concat(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    public static boolean containsIgnoreCase(String search, Set<String> haystack) {
        if(haystack == null) {
            return false;
        }
        for ( String rep : haystack )
        {
            if(rep.equalsIgnoreCase( search )) {
                return true;
            }
        }
        return false;
    }

    // Subtract the set b from the set a
    public static <T> Set<T> subtract(Set<T> a, Set<T> b) {
        Set<T> out = new HashSet<T>();
        for ( T it : a )
        {
            if(!b.contains( it )) {
                out.add( it );
            }
        }
        return out;
    }


    // Describe the difference between sets left and right, from the perspective of set left
    // Return the empty string if the sets are equal.
    public static <T> String diff(Set<T> left, Set<T> right) {
        Set<T> extra = subtract( left, right );
        Set<T> missing = subtract( right, left );
        if(extra.size() > 0 && missing.size() > 0) {
            return String.format( "extra elements: %s, missing elements: %s", extra, missing );
        } else if(extra.size() > 0) {
            return String.format( "extra elements: %s", extra );
        } else if(missing.size() > 0) {
            return String.format( "missing elements: %s", missing );
        } else {
            return "";
        }
    }
}
