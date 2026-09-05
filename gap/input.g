C2 := CyclicGroup(2);;
S3 := SymmetricGroup(3);;
W := WreathProduct(C2, S3);;
id := IdGroup(W);;
H := SmallGroup(id[1], id[2]);;

Print("C2 = ", C2, "\n");
Print("S3 = ", S3, "\n");
Print("W = C2 wr S3 = ", W, "\n");
Print("Size(W) = ", Size(W), "\n");
Print("IdGroup(W) = ", id, "\n");
Print("SmallGroup(", id[1], ", ", id[2], ") = ", H, "\n");
Print("Generators of W:\n", GeneratorsOfGroup(W), "\n");
Print("Generators of SmallGroup:\n", GeneratorsOfGroup(H), "\n");
