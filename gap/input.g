C2 := CyclicGroup(2);;
S3 := SymmetricGroup(3);;
W := WreathProduct(C2, S3);;
id := IdGroup(W);;
H := SmallGroup(id[1], id[2]);;

Print(Elements(H), "\n");
