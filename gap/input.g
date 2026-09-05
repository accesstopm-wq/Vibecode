C2 := CyclicGroup(2);;
S3 := SymmetricGroup(3);;
W := WreathProduct(C2, S3);;
A := SymmetricGroup(6);;
iso := IsomorphismGroups(W, A);;
H := Image(iso);;

Print(Elements(H), "\n");
